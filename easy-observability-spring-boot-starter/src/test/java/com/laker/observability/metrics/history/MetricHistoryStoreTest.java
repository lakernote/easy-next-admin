package com.laker.observability.metrics.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rrd4j.ConsolFun;
import org.rrd4j.DsType;
import org.rrd4j.core.ArcDef;
import org.rrd4j.core.DsDef;
import org.rrd4j.core.RrdDb;
import org.rrd4j.core.RrdDef;
import org.rrd4j.core.RrdToolkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetricHistoryStoreTest {
    private static final long MEBIBYTE = 1024L * 1024;

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MutableClock clock = new MutableClock(
            Instant.parse("2026-10-14T02:00:00Z"), ZoneOffset.ofHours(8));

    @Test
    void persistsRrdAndReadsMatchingPointsNewestFirstAfterRestart() {
        MetricHistoryStore store = store(64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        store.restore();
        store.recordMetricsSnapshot(List.of(metric("jvm.memory.used", 42, "VALUE")));
        clock.advance(Duration.ofMinutes(1));
        store.recordMetricsSnapshot(List.of(metric("jvm.memory.used", 1_042, "VALUE")));

        assertThat(rrdFiles()).hasSize(1);
        assertThat(metricMetadataFiles()).hasSize(1);
        assertThat(store.storageBytes()).isPositive().isLessThan(64 * MEBIBYTE);

        MetricHistoryStore restarted = store(64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        restarted.restore();
        List<MetricHistoryPoint> points = restarted.metricHistory(
                "jvm.memory.used", Map.of("area", "heap", "node", "local"), 10);

        assertThat(points).hasSize(2);
        assertThat(points).extracting(point -> point.measurements().get("VALUE"))
                .containsExactly(1_042D, 42D);
        assertThat(points).extracting(MetricHistoryPoint::capturedAt)
                .containsExactly("2026-10-14T10:01", "2026-10-14T10:00");
        assertThat(restarted.seriesCatalog()).singleElement().satisfies(series -> {
            assertThat(series.name()).isEqualTo("jvm.memory.used");
            assertThat(series.tags()).containsEntry("area", "heap").containsEntry("node", "local");
            assertThat(series.measurements()).containsExactly("VALUE");
        });
    }

    @Test
    void emptyTagsSelectOnlyTheExactUntaggedSeries() {
        MetricHistoryStore store = store(64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        store.restore();
        store.recordMetricsSnapshot(List.of(
                new MetricSample("same.metric", Map.of(), Map.of("VALUE", 1D)),
                new MetricSample("same.metric", Map.of("node", "local"),
                        Map.of("VALUE", 2D))));

        assertThat(store.metricHistory("same.metric", Map.of(), 10)).singleElement().satisfies(point -> {
            assertThat(point.tags()).isEmpty();
            assertThat(point.measurements()).containsEntry("VALUE", 1D);
        });
        assertThat(store.metricHistory("same.metric", Map.of("node", "local"), 10)).singleElement()
                .satisfies(point -> assertThat(point.measurements()).containsEntry("VALUE", 2D));
    }

    @Test
    void createsFixedOneMinuteFiveMinuteAndThirtyMinuteArchives() throws Exception {
        MetricHistoryStore store = store(64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        store.restore();
        store.recordMetricsSnapshot(List.of(metric("process.cpu.usage", 1, "VALUE")));

        try (RrdDb database = new RrdDb(rrdFiles().get(0).toString())) {
            assertThat(database.getRrdDef().getStep()).isEqualTo(60);
            ArcDef[] archives = database.getRrdDef().getArcDefs();
            assertThat(Arrays.stream(archives).map(ArcDef::getConsolFun)).containsOnly(ConsolFun.LAST);
            assertThat(Arrays.stream(archives).map(archive -> archive.getSteps() + ":" + archive.getRows()))
                    .containsExactlyInAnyOrder("1:1440", "5:2016", "30:1440");
        }
    }

    @Test
    void rejectsArchiveWithUnexpectedXFilesFactor() throws Exception {
        MetricHistoryStore initial = store(64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        initial.restore();
        initial.recordMetricsSnapshot(List.of(metric("process.cpu.usage", 1, "VALUE")));
        RrdToolkit.setArcXff(rrdFiles().get(0).toString(), ConsolFun.LAST, 1, 0.25);

        MetricHistoryStore restarted = store(64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        restarted.restore();

        assertThat(restarted.status().segmentCount()).isZero();
        assertThat(restarted.status().evictedSeries()).isEqualTo(1);
        assertThat(rrdFiles()).isEmpty();
    }

    @Test
    void rejectsDatasourceTypeHeartbeatAndBoundsDrift() throws Exception {
        List<RrdSchemaMutation> mutations = List.of(
                this::replaceGaugeWithCounter,
                path -> RrdToolkit.setDsHeartbeat(path.toString(), datasourceName(path), 999),
                path -> RrdToolkit.setDsMinMaxValue(path.toString(), datasourceName(path), 0, 100, false));

        for (int index = 0; index < mutations.size(); index++) {
            Path directory = tempDirectory.resolve("schema-" + index);
            MetricHistoryStore initial = store(directory, 30, 64 * MEBIBYTE, 8 * MEBIBYTE, 512);
            initial.restore();
            initial.recordMetricsSnapshot(List.of(metric("process.cpu.usage", 1, "VALUE")));
            Path rrd = filesEndingWith(directory, ".rrd").get(0);
            mutations.get(index).apply(rrd);

            MetricHistoryStore restarted = store(directory, 30,
                    64 * MEBIBYTE, 8 * MEBIBYTE, 512);
            restarted.restore();

            assertThat(restarted.status().segmentCount()).as("schema mutation %s", index).isZero();
            assertThat(restarted.status().evictedSeries()).as("schema mutation %s", index).isEqualTo(1);
            assertThat(filesEndingWith(directory, ".rrd")).isEmpty();
        }
    }

    @Test
    void growsRetentionArchiveWithoutLosingExistingHistory() throws Exception {
        MetricHistoryStore initial = store(30, 64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        initial.restore();
        initial.recordMetricsSnapshot(List.of(metric("process.cpu.usage", 1, "VALUE")));

        MetricHistoryStore changedRetention = store(60, 64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        changedRetention.restore();

        assertThat(changedRetention.status().segmentCount()).isEqualTo(1);
        assertThat(changedRetention.status().evictedSeries()).isZero();
        assertThat(changedRetention.metricHistory("process.cpu.usage",
                Map.of("area", "heap", "node", "local"), 10))
                .extracting(point -> point.measurements().get("VALUE"))
                .containsExactly(1D);
        assertRetentionRows(60 * 24 * 2);

        clock.advance(Duration.ofMinutes(1));
        changedRetention.recordMetricsSnapshot(List.of(metric("process.cpu.usage", 2, "VALUE")));

        assertThat(changedRetention.status().segmentCount()).isEqualTo(1);
        assertThat(changedRetention.metricHistory("process.cpu.usage",
                Map.of("area", "heap", "node", "local"), 10))
                .extracting(point -> point.measurements().get("VALUE"))
                .containsExactly(2D, 1D);
    }

    @Test
    void shrinksRetentionArchiveWithoutLosingHistoryInsideTheNewWindow() throws Exception {
        MetricHistoryStore initial = store(60, 64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        initial.restore();
        initial.recordMetricsSnapshot(List.of(metric("process.cpu.usage", 1, "VALUE")));

        MetricHistoryStore changedRetention = store(30, 64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        changedRetention.restore();

        assertThat(changedRetention.status().segmentCount()).isEqualTo(1);
        assertThat(changedRetention.status().evictedSeries()).isZero();
        assertThat(changedRetention.metricHistory("process.cpu.usage",
                Map.of("area", "heap", "node", "local"), 10))
                .extracting(point -> point.measurements().get("VALUE"))
                .containsExactly(1D);
        assertRetentionRows(30 * 24 * 2);
    }

    @Test
    void reportsCoverageFromObservedSamplesInsteadOfMetadataAge() {
        MetricHistoryStore store = store(64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        store.restore();
        store.recordMetricsSnapshot(List.of(metric("jvm.memory.used", 42, "VALUE")));
        clock.advance(Duration.ofMinutes(1));
        store.recordMetricsSnapshot(List.of(metric("jvm.memory.used", 43, "VALUE")));

        assertThat(store.status().oldestSegmentDate()).isEqualTo("2026-10-14");
        assertThat(store.status().coverageDays()).isEqualTo(1);

        clock.advance(Duration.ofDays(10));
        assertThat(store.status().coverageDays()).isEqualTo(1);

        MetricHistoryStore restarted = store(64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        restarted.restore();

        assertThat(restarted.status().oldestSegmentDate()).isEqualTo("2026-10-14");
        assertThat(restarted.status().coverageDays()).isEqualTo(1);
        assertThat(restarted.status().segmentCount()).isEqualTo(1);
    }

    @Test
    void expandsMeasurementSchemaAndKeepsItReadableAfterRestart() {
        MetricHistoryStore store = store(64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        store.restore();
        store.recordMetricsSnapshot(List.of(metric("http.server.requests", 1, "COUNT")));
        clock.advance(Duration.ofMinutes(1));
        store.recordMetricsSnapshot(List.of(new MetricSample("http.server.requests",
                Map.of("area", "heap", "node", "local"),
                Map.of("COUNT", 2D, "TOTAL_TIME", 0.5D, "MAX", 0.4D))));

        MetricHistoryStore restarted = store(64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        restarted.restore();
        List<MetricHistoryPoint> points = restarted.metricHistory(
                "http.server.requests", Map.of("area", "heap", "node", "local"), 10);

        assertThat(points).hasSize(2);
        assertThat(points.get(0).measurements())
                .containsEntry("COUNT", 2D)
                .containsEntry("TOTAL_TIME", 0.5D)
                .containsEntry("MAX", 0.4D);
    }

    @Test
    void keepsFixedDirectoryCapacityAndReportsDroppedSnapshot() {
        MetricHistoryStore store = store(90_000, 80_000, 10);
        store.restore();
        store.recordMetricsSnapshot(List.of(
                metric("test.one", 1, "VALUE"),
                metric("test.two", 2, "VALUE"),
                metric("test.three", 3, "VALUE")));

        MetricHistoryStore.StorageStatus status = store.status();
        assertThat(status.segmentCount()).isEqualTo(2);
        assertThat(status.storageBytes()).isLessThanOrEqualTo(90_000);
        assertThat(status.maxStorageBytes()).isEqualTo(90_000);
        assertThat(status.maxSegmentBytes()).isEqualTo(80_000);
        assertThat(status.retentionDays()).isEqualTo(30);
        assertThat(status.droppedWrites()).isEqualTo(1);
        assertThat(status.rejectedSeries()).isEqualTo(1);
        assertThat(status.evictedSeries()).isZero();
    }

    @Test
    void admitsCoreSeriesImmediatelyByEvictingOptionalSeriesAndNeverLetsOptionalDisplaceCore() {
        MetricHistoryStore store = store(64 * MEBIBYTE, 8 * MEBIBYTE, 1);
        store.restore();
        store.recordMetricsSnapshot(List.of(metric("http.server.requests", 1, "COUNT")));
        assertThat(store.metricHistory("http.server.requests", defaultTags(), 10)).hasSize(1);

        clock.advance(Duration.ofMinutes(1));
        store.recordMetricsSnapshot(List.of(metric("jvm.memory.used", 2, "VALUE")));

        assertThat(store.metricHistory("jvm.memory.used", defaultTags(), 10)).hasSize(1);
        assertThat(store.metricHistory("http.server.requests", defaultTags(), 10)).isEmpty();
        assertThat(store.status().evictedSeries()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(1));
        store.recordMetricsSnapshot(List.of(metric("easy.api.access", 3, "COUNT")));

        assertThat(store.metricHistory("jvm.memory.used", defaultTags(), 10)).hasSize(1);
        assertThat(store.metricHistory("easy.api.access", defaultTags(), 10)).isEmpty();
        assertThat(store.status().rejectedSeries()).isEqualTo(1);
        assertThat(store.status().evictedSeries()).isEqualTo(1);
    }

    @Test
    void writesCoreSeriesBeforeOptionalSeriesEvenWhenSnapshotArrivesInReversePriorityOrder() {
        MetricHistoryStore store = store(64 * MEBIBYTE, 8 * MEBIBYTE, 1);
        store.restore();

        store.recordMetricsSnapshot(List.of(
                metric("http.server.requests", 1, "COUNT"),
                metric("process.cpu.usage", 2, "VALUE")));

        assertThat(store.metricHistory("process.cpu.usage", defaultTags(), 10)).hasSize(1);
        assertThat(store.metricHistory("http.server.requests", defaultTags(), 10)).isEmpty();
        assertThat(store.status().rejectedSeries()).isEqualTo(1);
        assertThat(store.status().evictedSeries()).isZero();
    }

    @Test
    void capacityEnforcementOnRestartKeepsCoreSeriesBeforeOptionalSeries() {
        MetricHistoryStore initial = store(64 * MEBIBYTE, 8 * MEBIBYTE, 2);
        initial.restore();
        initial.recordMetricsSnapshot(List.of(
                metric("jvm.threads.live", 5, "VALUE"),
                metric("http.server.requests", 1, "COUNT")));
        assertThat(initial.status().segmentCount()).isEqualTo(2);

        MetricHistoryStore constrained = store(64 * MEBIBYTE, 8 * MEBIBYTE, 1);
        constrained.restore();

        assertThat(constrained.metricHistory("jvm.threads.live", defaultTags(), 10)).hasSize(1);
        assertThat(constrained.metricHistory("http.server.requests", defaultTags(), 10)).isEmpty();
        assertThat(constrained.status().evictedSeries()).isEqualTo(1);
    }

    @Test
    void removesExistingSeriesWhenRestartedWithLowerPerSeriesLimit() throws Exception {
        MetricHistoryStore initial = store(64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        initial.restore();
        initial.recordMetricsSnapshot(List.of(metric("jvm.memory.used", 42, "VALUE")));

        long existingRrdBytes = Files.size(rrdFiles().get(0));
        long loweredSeriesLimit = Math.max(1, existingRrdBytes / 2);
        MetricHistoryStore constrained = store(64 * MEBIBYTE, loweredSeriesLimit, 512);
        constrained.restore();

        MetricHistoryStore.StorageStatus restoredStatus = constrained.status();
        assertThat(restoredStatus.segmentCount()).isZero();
        assertThat(restoredStatus.evictedSeries()).isEqualTo(1);
        assertThat(restoredStatus.rejectedSeries()).isZero();
        assertThat(restoredStatus.droppedWrites()).isZero();
        assertThat(rrdFiles()).isEmpty();
        assertThat(metricMetadataFiles()).isEmpty();

        constrained.recordMetricsSnapshot(List.of(metric("jvm.memory.used", 43, "VALUE")));

        assertThat(constrained.status().segmentCount()).isZero();
        assertThat(constrained.status().evictedSeries()).isEqualTo(1);
        assertThat(constrained.status().rejectedSeries()).isEqualTo(1);
        assertThat(constrained.status().droppedWrites()).isEqualTo(1);
        assertThat(rrdFiles()).isEmpty();
        assertThat(metricMetadataFiles()).isEmpty();
    }

    @Test
    void removesStoppedSeriesAfterRetentionAndDeletesLegacyMtsFiles() throws Exception {
        Files.writeString(tempDirectory.resolve("metrics-2026-10-14.mts"), "legacy");
        MetricHistoryStore store = store(64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        store.restore();
        assertThat(legacyFiles()).isEmpty();

        store.recordMetricsSnapshot(List.of(metric("process.cpu.usage", 1, "VALUE")));
        assertThat(store.status().segmentCount()).isEqualTo(1);
        clock.advance(Duration.ofDays(31));
        store.pruneExpired();

        assertThat(store.status().segmentCount()).isZero();
        assertThat(rrdFiles()).isEmpty();
        assertThat(metricMetadataFiles()).isEmpty();
    }

    @Test
    void scansCapacityOncePerBatchAndCachesRepeatedCapacityRejectionsBriefly() {
        MetricHistoryStore store = store(64 * MEBIBYTE, 8 * MEBIBYTE, 1);
        store.restore();
        store.recordMetricsSnapshot(List.of(metric("jvm.memory.used", 1, "VALUE")));
        clock.advance(Duration.ofMinutes(1));

        long scansBeforeRejection = store.directoryScanCount();
        long attemptsBeforeRejection = store.seriesCreationAttemptCount();
        MetricSample rejected = metric("http.server.requests", 1, "COUNT");
        store.recordMetricsSnapshot(List.of(rejected, rejected));

        assertThat(store.directoryScanCount()).isEqualTo(scansBeforeRejection + 1);
        assertThat(store.seriesCreationAttemptCount()).isEqualTo(attemptsBeforeRejection + 1);
        assertThat(store.rejectedSeriesCacheSize()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(1));
        store.recordMetricsSnapshot(List.of(rejected));

        assertThat(store.directoryScanCount()).isEqualTo(scansBeforeRejection + 1);
        assertThat(store.seriesCreationAttemptCount()).isEqualTo(attemptsBeforeRejection + 1);

        clock.advance(Duration.ofMinutes(5));
        store.recordMetricsSnapshot(List.of(rejected));

        assertThat(store.directoryScanCount()).isEqualTo(scansBeforeRejection + 2);
        assertThat(store.seriesCreationAttemptCount()).isEqualTo(attemptsBeforeRejection + 2);
    }

    @Test
    void doesNotRescanDirectoryForSteadyStateWritesToKnownSeries() {
        MetricHistoryStore store = store(64 * MEBIBYTE, 8 * MEBIBYTE, 512);
        store.restore();
        store.recordMetricsSnapshot(List.of(metric("jvm.memory.used", 1, "VALUE")));
        long scansAfterAdmission = store.directoryScanCount();

        clock.advance(Duration.ofMinutes(1));
        store.recordMetricsSnapshot(List.of(metric("jvm.memory.used", 2, "VALUE")));

        assertThat(store.directoryScanCount()).isEqualTo(scansAfterAdmission);
        assertThat(store.metricHistory("jvm.memory.used", defaultTags(), 10)).hasSize(2);
    }

    @Test
    void boundsAdmissionAttemptsAndCachesOnlyEvaluatedRejectionsUnderHighCardinalityInput() {
        MetricHistoryStore store = store(64 * MEBIBYTE, 8 * MEBIBYTE, 1);
        store.restore();
        store.recordMetricsSnapshot(List.of(metric("jvm.memory.used", 1, "VALUE")));
        clock.advance(Duration.ofMinutes(1));
        long scansBeforeRejection = store.directoryScanCount();
        long attemptsBeforeRejection = store.seriesCreationAttemptCount();

        List<MetricSample> rejected = java.util.stream.IntStream.range(0, 300)
                .mapToObj(index -> new MetricSample("http.server.requests",
                        Map.of("uri", "/dynamic/" + index), Map.of("COUNT", 1D)))
                .toList();
        store.recordMetricsSnapshot(rejected);

        assertThat(store.directoryScanCount()).isEqualTo(scansBeforeRejection + 1);
        assertThat(store.seriesCreationAttemptCount()).isEqualTo(attemptsBeforeRejection + 1);
        assertThat(store.rejectedSeriesCacheSize()).isEqualTo(1);
        assertThat(store.status().segmentCount()).isEqualTo(1);
    }

    @Test
    void disabledStoreDoesNotCreateRrdFiles() {
        MetricHistoryStore store = new MetricHistoryStore(objectMapper,
                tempDirectory.toString(), 30, 64 * MEBIBYTE, 8 * MEBIBYTE, 512, clock, false);
        store.restore();
        store.recordMetricsSnapshot(List.of(metric("ignored", 1, "VALUE")));

        assertThat(store.metricHistory("ignored", Map.of(), 10)).isEmpty();
        assertThat(store.seriesCatalog()).isEmpty();
        assertThat(store.status().enabled()).isFalse();
        assertThat(rrdFiles()).isEmpty();
    }

    private MetricHistoryStore store(long maxBytes, long maxSeriesBytes, int maxSeries) {
        return store(30, maxBytes, maxSeriesBytes, maxSeries);
    }

    private MetricHistoryStore store(int retentionDays, long maxBytes, long maxSeriesBytes, int maxSeries) {
        return store(tempDirectory, retentionDays, maxBytes, maxSeriesBytes, maxSeries);
    }

    private MetricHistoryStore store(Path directory, int retentionDays,
                                             long maxBytes, long maxSeriesBytes, int maxSeries) {
        return new MetricHistoryStore(objectMapper, directory.toString(), retentionDays,
                maxBytes, maxSeriesBytes, maxSeries, clock, true);
    }

    private MetricSample metric(String name, int value, String measurement) {
        return new MetricSample(name,
                defaultTags(), Map.of(measurement, (double) value));
    }

    private static Map<String, String> defaultTags() {
        return Map.of("area", "heap", "node", "local");
    }

    private List<Path> rrdFiles() {
        return filesEndingWith(".rrd");
    }

    private List<Path> metricMetadataFiles() {
        return filesEndingWith(".meta.json");
    }

    private List<Path> legacyFiles() {
        return filesEndingWith(".mts");
    }

    private void assertRetentionRows(int expectedRows) throws Exception {
        try (RrdDb database = new RrdDb(rrdFiles().get(0).toString())) {
            assertThat(Arrays.stream(database.getRrdDef().getArcDefs())
                    .filter(archive -> archive.getSteps() == 30)
                    .map(ArcDef::getRows))
                    .containsExactly(expectedRows);
        }
    }

    private List<Path> filesEndingWith(String suffix) {
        return filesEndingWith(tempDirectory, suffix);
    }

    private List<Path> filesEndingWith(Path directory, String suffix) {
        if (!Files.isDirectory(directory)) return List.of();
        try (var paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String datasourceName(Path path) throws Exception {
        try (RrdDb database = new RrdDb(path.toString())) {
            return database.getDsNames()[0];
        }
    }

    private void replaceGaugeWithCounter(Path path) throws Exception {
        RrdDef original;
        try (RrdDb database = new RrdDb(path.toString())) {
            original = database.getRrdDef();
        }
        Files.delete(path);
        RrdDef replacement = new RrdDef(path.toString(), original.getStartTime(),
                original.getStep(), original.getVersion());
        for (DsDef datasource : original.getDsDefs()) {
            replacement.addDatasource(datasource.getDsName(), DsType.COUNTER, datasource.getHeartbeat(),
                    datasource.getMinValue(), datasource.getMaxValue());
        }
        for (ArcDef archive : original.getArcDefs()) {
            replacement.addArchive(archive.getConsolFun(), archive.getXff(), archive.getSteps(), archive.getRows());
        }
        try (RrdDb ignored = new RrdDb(replacement)) {
            // Replacement deliberately keeps the same names and archive rows with the wrong datasource type.
        }
    }

    @FunctionalInterface
    private interface RrdSchemaMutation {
        void apply(Path path) throws Exception;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
