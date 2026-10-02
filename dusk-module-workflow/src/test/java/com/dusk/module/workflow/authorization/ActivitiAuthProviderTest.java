package com.dusk.module.workflow.authorization;

import com.dusk.common.core.auth.permission.IPermissionDefinitionContext;
import com.dusk.common.core.auth.permission.MultiTenancySides;
import com.dusk.common.core.auth.permission.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ActivitiAuthProvider} 单元测试：校验权限树被完整注册。
 */
@ExtendWith(MockitoExtension.class)
class ActivitiAuthProviderTest {

    @Mock
    private IPermissionDefinitionContext context;

    @Test
    @DisplayName("setPermissions：注册 Pages.Activiti 权限树")
    void setPermissions() {
        Permission main = new Permission(ActivitiAuthProvider.PAGES_ACTIVITI, "工作流", MultiTenancySides.Tenant);
        when(context.createPermission(anyString(), anyString(), any(MultiTenancySides.class))).thenReturn(main);

        new ActivitiAuthProvider().setPermissions(context);

        verify(context).createPermission(ActivitiAuthProvider.PAGES_ACTIVITI, "工作流", MultiTenancySides.Tenant);

        // 主权限下应挂载 模型管理 / 流程管理 / 任务管理 三个一级子权限
        assertThat(main.getChildren()).extracting(Permission::getName)
                .containsExactly(
                        ActivitiAuthProvider.PAGES_ACTIVITI_MODEL,
                        ActivitiAuthProvider.PAGES_ACTIVITI_PROCESS,
                        ActivitiAuthProvider.PAGES_ACTIVITI_TASK);

        Permission model = main.getChildren().get(0);
        assertThat(model.getChildren()).extracting(Permission::getName)
                .containsExactly(
                        ActivitiAuthProvider.PAGES_ACTIVITI_MODEL_SAVE,
                        ActivitiAuthProvider.PAGES_ACTIVITI_MODEL_DEPLOY,
                        ActivitiAuthProvider.PAGES_ACTIVITI_MODEL_DELETE);

        Permission process = main.getChildren().get(1);
        assertThat(process.getChildren()).extracting(Permission::getName)
                .containsExactly(ActivitiAuthProvider.PAGES_ACTIVITI_PROCESS_DELETE);

        // 每个子权限均显式声明租户侧
        verify(context, times(1)).createPermission(anyString(), anyString(), any(MultiTenancySides.class));
    }
}
