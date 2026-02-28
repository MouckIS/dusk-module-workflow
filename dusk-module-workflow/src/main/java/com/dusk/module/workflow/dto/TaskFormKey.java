package com.dusk.module.workflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务节点 formKey 配置模型
 * <p>
 * 流程设计器中每个UserTask节点的formKey字段存储一个JSON配置，反序列化为此对象。
 * 用于控制候选人/角色、撤回权限、消息通知、抄送等行为。
 * </p>
 * <p>
 * formKey JSON 示例：
 * <pre>
 * {
 *   "activiti": {
 *     "candidateRoles": "部门经理,总监",
 *     "candidatePsns": "1001,1002",
 *     "callBackPre": true,
 *     "notice": { "addTodo": true, "appPush": true },
 *     "carbonCopy": {
 *       "enabled": true,
 *       "ccUserIds": "2001,2002",
 *       "ccRoles": "财务",
 *       "template": "您收到一条审批抄送"
 *     }
 *   }
 * }
 * </pre>
 * </p>
 *
 * @author pengjian
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

        // 抄送配置
        private CarbonCopy carbonCopy = new CarbonCopy();

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

    /**
     * 抄送配置
     */
    @Getter
    @Setter
    public static class CarbonCopy {
        /**
         * 是否启用抄送
         */
        private boolean enabled = false;

        /**
         * 抄送的用户ID列表，多个用逗号分隔
         */
        private String ccUserIds;

        /**
         * 抄送的角色名称列表，多个用逗号分隔（会解析为具体用户）
         */
        private String ccRoles;

        /**
         * 抄送消息模板
         */
        private String template;
    }
}
