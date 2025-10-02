package com.dusk.module.workflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pengjian
 * @date 2021-11-30 14:41
 */
@Getter
@Setter
public class TaskFormKey {
    private Activiti activiti = new Activiti();

    /**
     * 消息提醒
     */
    @Getter
    @Setter
    public static class Activiti {
        //候选角色，用角色名，多个用英文逗号分开，新的配置请使用列表配置
        private Object candidateRoles;
        //候选人，用人员id，多个用英文逗号分开，新的配置请使用列表配置
        private Object candidatePsns;

        // 是否允许撤回到上一节点
        private boolean callBackPre;

        private Notice notice = new Notice();

        public String getCandidateRoles() {
            return toStr(candidateRoles);
        }

        //为了兼容之前的字符串配置
        public String getCandidatePsns() {
            return toStr(candidatePsns);
        }

        /**
         * 如果是列表用","拼接成字符串，如果是字符串，直接返回字符串，其他返回空字符串""
         *
         * @param o
         * @return
         */
        private String toStr(Object o) {
            if (o == null) {
                return "";
            } else if (o instanceof String) {
                return (String) o;
            } else if (o instanceof List) {
                List<Object> list = (List<Object>) o;
                List<String> resultList = new ArrayList<>();
                for (Object o1 : list) {
                    resultList.add(o1.toString());
                }
                return String.join(",", resultList);
            } else {
                return "";
            }
        }
    }

    /**
     * 消息提醒
     */
    @Getter
    @Setter
    public static class Notice {
        /**
         * 添加待办
         */
        private boolean addTodo = true;

        /**
         * app顶部消息推送， addTodo开启的时候才会生效
         */
        private boolean appPush = true;
    }
}
