package com.dusk.module.workflow.controller;

import com.dusk.common.model.dto.PagedResultDto;
import com.dusk.module.workflow.dto.GetModelsInput;
import com.dusk.module.workflow.dto.ModelDto;
import com.dusk.module.workflow.service.IModelService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ModelController} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ModelControllerTest {

    @Mock
    private IModelService modelService;

    @Mock
    private HttpServletResponse response;

    @Mock
    private ServletOutputStream servletOutputStream;

    @InjectMocks
    private ModelController controller;

    @Test
    @DisplayName("getModels 委托到 service")
    void getModels() {
        GetModelsInput input = new GetModelsInput();
        PagedResultDto<ModelDto> expected = new PagedResultDto<>(0, List.of());
        when(modelService.getModels(input)).thenReturn(expected);

        assertThat(controller.getModels(input)).isSameAs(expected);
    }

    @Test
    @DisplayName("rollBackByKey 委托")
    void rollBackByKey() {
        when(modelService.rollBackByKey("key", 1)).thenReturn(true);

        assertThat(controller.rollBackByKey("key", 1)).isTrue();
    }

    @Test
    @DisplayName("removeModelByKey 委托")
    void removeModelByKey() {
        when(modelService.removeModelByKey("key")).thenReturn(true);

        assertThat(controller.removeModelByKey("key")).isTrue();
    }

    @Test
    @DisplayName("deploy 委托")
    void deploy() {
        controller.deploy("m1");

        verify(modelService).deploy("m1");
    }

    @Test
    @DisplayName("getModelSvg：写出模型XML")
    void getModelSvg() throws Exception {
        byte[] data = "xml".getBytes(StandardCharsets.UTF_8);
        when(modelService.getSvgXmlByModelId("m1")).thenReturn(data);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        controller.getModelSvg("m1", response);

        verify(response).setContentType(MediaType.APPLICATION_XML_VALUE);
        verify(servletOutputStream).write(data);
        verify(servletOutputStream).flush();
        verify(servletOutputStream).close();
    }

    @Test
    @DisplayName("getModelSvgByKey：写出模型XML")
    void getModelSvgByKey() throws Exception {
        byte[] data = "xml".getBytes(StandardCharsets.UTF_8);
        when(modelService.getSvgXmlByKey("key")).thenReturn(data);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        controller.getModelSvgByKey("key", response);

        verify(response).setContentType(MediaType.APPLICATION_XML_VALUE);
        verify(servletOutputStream).write(data);
    }

    @Test
    @DisplayName("importModelBySvg：读取上传流并转换")
    void importModelBySvg() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(modelService.convertInputStreamToModel(any())).thenReturn("newId");

        controller.importModelBySvg(file);

        verify(modelService).convertInputStreamToModel(any());
    }

    @Test
    @DisplayName("save：字符串SVG转换为模型")
    void save() throws Exception {
        when(modelService.convertInputStreamToModel(any())).thenReturn("newId");

        assertThat(controller.save("<xml/>")).isEqualTo("newId");

        verify(modelService).convertInputStreamToModel(any());
    }
}
