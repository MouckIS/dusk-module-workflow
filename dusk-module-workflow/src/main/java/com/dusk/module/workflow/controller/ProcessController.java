package com.dusk.module.workflow.controller;

import com.dusk.common.core.annotation.Authorize;
import com.dusk.common.core.controller.CruxBaseController;
import com.dusk.common.core.dto.PagedResultDto;
import com.dusk.module.workflow.authorization.ActivitiAuthProvider;
import com.dusk.module.workflow.dto.GetProcessesInput;
import com.dusk.module.workflow.dto.ProcessDefDto;
import com.dusk.module.workflow.service.IProcessService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

/**
 * @author kefuming
 * @date 2020-07-22 13:55
 */
@RestController
@RequestMapping("/process")
@Api(description = "流程管理", tags = "Process")
public class ProcessController extends CruxBaseController {
    @Autowired
    IProcessService processService;

    @ApiOperation("分页获取流程")
    @GetMapping
    public PagedResultDto<ProcessDefDto> list(GetProcessesInput input) {
        return processService.getProcesses(input);
    }

    @ApiOperation("获取流程图片或者xml定义")
    @SneakyThrows
    @GetMapping(value = "/resource/{proInsId}/{resType}")
    public void resourceRead(@PathVariable String proInsId, @PathVariable String resType, HttpServletResponse response) {
        String contentType = "";
        if ("image".equals(resType)) {
            contentType = MediaType.APPLICATION_XML_VALUE;
        } else {
            contentType = MediaType.IMAGE_PNG_VALUE;
        }
        ServletOutputStream outputStream = response.getOutputStream();
        byte[] data = processService.getResource(proInsId, resType);
        response.setContentType(contentType);
        response.setCharacterEncoding("UTF-8");
        outputStream.write(data);
        outputStream.flush();
        outputStream.close();
    }

    @ApiOperation("删除流程实例")
    @DeleteMapping("/{deploymentId}")
    @Authorize(ActivitiAuthProvider.PAGES_ACTIVITI_PROCESS_DELETE)
    public void deleteProcIns(@PathVariable String deploymentId) {
        processService.removeProcIns(deploymentId);
    }
}
