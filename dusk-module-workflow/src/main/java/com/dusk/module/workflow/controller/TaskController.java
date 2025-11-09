package com.dusk.module.workflow.controller;

import com.dusk.common.core.controller.CruxBaseController;
import com.dusk.common.core.dto.PagedAndSortedInputDto;
import com.dusk.common.core.dto.PagedResultDto;
import com.dusk.module.workflow.service.IActTaskService;
import com.dusk.workflow.dto.WorkflowTaskDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author kefuming
 * @date 2020-07-22 14:23
 */
@RestController
@RequestMapping("/task")
@Tag(description = "任务管理", name = "ActivitiTask")
public class TaskController extends CruxBaseController {
    @Autowired
    IActTaskService actTaskService;

    @GetMapping
    public PagedResultDto<WorkflowTaskDto> getTask(PagedAndSortedInputDto inputDto) {
        return actTaskService.getTasks(inputDto);
    }

    @SneakyThrows
    @GetMapping("/view/{id}")
    public void viewCurrentImage(@PathVariable String id, HttpServletResponse response) {
        byte[] bytes = actTaskService.viewByTaskId(id);
        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        response.setCharacterEncoding("UTF-8");
        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.write(bytes);
        outputStream.flush();
        outputStream.close();
    }
}
