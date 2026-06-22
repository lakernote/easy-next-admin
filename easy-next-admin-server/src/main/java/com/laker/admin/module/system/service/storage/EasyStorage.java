package com.laker.admin.module.system.service.storage;

import java.io.InputStream;

public interface EasyStorage {

    String store(InputStream inputStream, long contentLength, String contentType, String fileName);

    boolean exists(String filePath);

    InputStream read(String filePath);

    void delete(String filePath);

    String getUrl(String filePath);
}
