package com.laker.admin.common.util;

import com.laker.admin.module.system.dto.MenuVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EasyTreeUtilTest {

    @Test
    void shouldBuildMenuTreeWhenIdsArePresent() {
        MenuVo root = menu(1L, 0L, "系统管理");
        MenuVo child = menu(2L, 1L, "用户管理");

        List<MenuVo> tree = EasyTreeUtil.toTree(List.of(root, child), 0L);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getTitle()).isEqualTo("系统管理");
        assertThat(tree.get(0).getChildren()).extracting(MenuVo::getTitle).containsExactly("用户管理");
    }

    @Test
    void shouldIgnoreNullNodeIdInsteadOfThrowing() {
        MenuVo root = menu(null, 0L, "异常菜单");
        MenuVo orphan = menu(2L, 1L, "孤立菜单");

        List<MenuVo> tree = EasyTreeUtil.toTree(List.of(root, orphan), 0L);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getTitle()).isEqualTo("异常菜单");
        assertThat(tree.get(0).getChildren()).isNull();
    }

    private static MenuVo menu(Long id, Long pid, String title) {
        MenuVo menu = new MenuVo();
        menu.setId(id);
        menu.setPid(pid);
        menu.setTitle(title);
        return menu;
    }
}
