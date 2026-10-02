package com.dusk.module.workflow.controller;

import com.dusk.common.model.dto.PagedAndSortedInputDto;
import com.dusk.common.model.dto.PagedResultDto;
import com.dusk.module.workflow.service.IActTaskService;
import com.dusk.workflow.dto.WorkflowTaskDto;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TaskController} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock
    private IActTaskService actTaskService;

    @Mock
    private HttpServletResponse response;

    @Mock
    private ServletOutputStream servletOutputStream;

    @InjectMocks
    private TaskController controller;

    @Test
    @DisplayName("getTask 委托到 service")
    void getTask() {
        PagedAndSortedInputDto input = new PagedAndSortedInputDto();
        PagedResultDto<WorkflowTaskDto> expected = new PagedResultDto<>(0, List.of());
        when(actTaskService.getTasks(input)).thenReturn(expected);

        assertThat(controller.getTask(input)).isSameAs(expected);
    }

    @Test
    @DisplayName("viewCurrentImage：写出PNG图片")
    void viewCurrentImage() throws Exception {
        byte[] data = "png".getBytes(StandardCharsets.UTF_8);
        when(actTaskService.viewByTaskId("t1")).thenReturn(data);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        controller.viewCurrentImage("t1", response);

        verify(response).setContentType(MediaType.IMAGE_PNG_VALUE);
        verify(response).setCharacterEncoding("UTF-8");
        verify(servletOutputStream).write(data);
        verify(servletOutputStream).flush();
        verify(servletOutputStream).close();
    }
}
