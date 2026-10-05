package com.dusk.module.workflow.controller;

import com.dusk.common.model.dto.PagedResultDto;
import com.dusk.module.workflow.dto.GetProcessesInput;
import com.dusk.module.workflow.dto.ProcessDefDto;
import com.dusk.module.workflow.service.IProcessService;
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
 * {@link ProcessController} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ProcessControllerTest {

    @Mock
    private IProcessService processService;

    @Mock
    private HttpServletResponse response;

    @Mock
    private ServletOutputStream servletOutputStream;

    @InjectMocks
    private ProcessController controller;

    @Test
    @DisplayName("list 委托到 service")
    void list() {
        GetProcessesInput input = new GetProcessesInput();
        PagedResultDto<ProcessDefDto> expected = new PagedResultDto<>(0, List.of());
        when(processService.getProcesses(input)).thenReturn(expected);

        assertThat(controller.list(input)).isSameAs(expected);
    }

    @Test
    @DisplayName("resourceRead：resType=image 时 contentType 为 XML")
    void resourceReadImage() throws Exception {
        byte[] data = "x".getBytes(StandardCharsets.UTF_8);
        when(processService.getResource("p1", "image")).thenReturn(data);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        controller.resourceRead("p1", "image", response);

        verify(response).setContentType(MediaType.APPLICATION_XML_VALUE);
        verify(response).setCharacterEncoding("UTF-8");
        verify(servletOutputStream).write(data);
        verify(servletOutputStream).flush();
        verify(servletOutputStream).close();
    }

    @Test
    @DisplayName("resourceRead：其他 resType 时 contentType 为 PNG")
    void resourceReadXml() throws Exception {
        byte[] data = "x".getBytes(StandardCharsets.UTF_8);
        when(processService.getResource("p1", "xml")).thenReturn(data);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        controller.resourceRead("p1", "xml", response);

        verify(response).setContentType(MediaType.IMAGE_PNG_VALUE);
        verify(servletOutputStream).write(data);
    }

    @Test
    @DisplayName("deleteProcIns 委托")
    void deleteProcIns() {
        controller.deleteProcIns("dep1");

        verify(processService).removeProcIns("dep1");
    }
}
