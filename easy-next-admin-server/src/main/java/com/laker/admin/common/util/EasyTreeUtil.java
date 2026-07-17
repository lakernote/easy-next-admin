package com.laker.admin.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EasyTreeUtil {

    private EasyTreeUtil() {
        // Prevent instantiation
    }

    public interface TreeNode<T extends TreeNode<T>> {
        Long getId();

        Long getPid();

        List<T> getChildren();

        void setChildren(List<T> children);
    }

    public static <T extends TreeNode<T>> List<T> toTree(List<T> treeList, Long pid) {
        List<T> retList = new ArrayList<>();
        for (T parent : treeList) {
            if (Objects.equals(pid, parent.getPid())) {
                retList.add(findChildren(parent, treeList));
            }
        }
        return retList;
    }

    private static <T extends TreeNode<T>> T findChildren(T parent, List<T> treeList) {
        for (T child : treeList) {
            if (parent.getId() != null && Objects.equals(parent.getId(), child.getPid())) {
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(findChildren(child, treeList));
            }
        }
        return parent;
    }
}
