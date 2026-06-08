package org.example.yunpicturebackend.controller;

import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import lombok.extern.slf4j.Slf4j;
import org.example.yunpicturebackend.annotation.AuthCheck;
import org.example.yunpicturebackend.common.BaseResponse;
import org.example.yunpicturebackend.common.ResultUtils;
import org.example.yunpicturebackend.constant.UserConstant;
import org.example.yunpicturebackend.exception.BusinessException;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.example.yunpicturebackend.manager.CosManager;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    /**
     * 腾讯云 COS 对象存储操作抽象层（Manager）
     */
    @Resource
    private CosManager cosManager;

    /**
     * 测试文件上传至 COS 对象存储
     * <p>
     * 业务逻辑：接收前端上传的 MultipartFile -> 转存为本地临时文件 -> 调用 COS SDK 上传临时文件 -> 清理本地临时文件。
     * 注意：该接口主要用于打通和测试上传链路，因此添加了管理员权限校验。
     *
     * @param multipartFile 接收到的前端上传的文件对象（Spring MVC 自动封装）
     * @return 统一返回体，包含文件在 COS 中的相对路径（对象键 Key）
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/upload")
    public BaseResponse<String> testUploadFile(@RequestPart("file") MultipartFile multipartFile) {

        // 1. 获取上传文件的原始名称（例如：avatar.png）
        String filename = multipartFile.getOriginalFilename();

        // 2. 拼接文件在 COS 中的存储路径（即对象键 Key）
        // COS 对象键不建议以 "/" 开头，否则在控制台可能会看到一个空名字的顶层文件夹，直接以目录名开头即可
        String filepath = String.format("test/%s", filename);

        File file = null;
        try {
            // 3. 创建本地临时文件
            // 目的：腾讯云 COS SDK 接收的是 java.io.File 类型，而 Spring Web 接收的是 MultipartFile 内存流/分片流，需要进行中转。
            file = File.createTempFile(filename, null);

            // 4. 将 MultipartFile 的数据流写入到本地临时文件中
            multipartFile.transferTo(file);

            // 5. 调用封装好的 CosManager，将临时文件上传到腾讯云 COS
            cosManager.putObject(filepath, file);

            // 6. 上传成功，返回在 COS 中的存储路径
            return ResultUtils.success(filepath);
        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 7. 【关键】无论上传成功还是失败，都必须在 finally 块中清理本地临时文件
            // 原因：如果不清理，每次上传都会在服务器产生一个临时文件，高并发下会迅速撑爆服务器所在磁盘（磁盘满导致整个服务宕机）
            if (file != null) {
                boolean delete = file.delete();
                if (!delete) {
                    // 若删除失败，记录告警日志，方便后续运维排查僵尸文件
                    log.error("file delete error, filepath = {}", filepath);
                }
            }
        }
    }



    /**
     * 测试从 COS 对象存储下载文件
     * <p>
     * 业务逻辑：接收前端传入的文件路径 -> 调用 COS SDK 获取文件流 -> 将流式数据写入 HTTP 响应中返回给前端。
     * 注意：该接口主要用于打通和测试下载链路，因此添加了管理员权限校验。
     *
     * @param filepath 文件在 COS 中的绝对路径（对象键 Key），例如："test/avatar.png"
     * @param response HTTP 响应对象，用于向客户端返回文件流
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/test/download")
    public void testDownloadFile(String filepath, HttpServletResponse response) throws IOException {
        COSObjectInputStream cosObjectInput = null;
        try {
            // 1. 调用封装好的 CosManager，根据文件路径从 COS 远程获取对象
            COSObject cosObject = cosManager.getObject(filepath);
            cosObjectInput = cosObject.getObjectContent();

            // 2. 提取纯文件名（例如将 "test/avatar.png" 截取为 "avatar.png"）
            // 目的是让用户下载时看到的是实际的文件名，而不是带有目录前缀的乱码名
            String filename = new File(filepath).getName();
            // 提示：实际生产环境中，如果 filename 含有中文，建议使用 URLEncoder.encode(filename, "UTF-8") 处理，防止浏览器下载时文件名乱码

            // 3. 设置 HTTP 响应头
            // 告诉浏览器这是一个二进制文件流
            response.setContentType("application/octet-stream;charset=UTF-8");
            // 告诉浏览器这是一个附件，并指定下载时的默认文件名
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            // 4. 将文件流分块写入响应的输出流中
            // 优化点：切忌使用 IOUtils.toByteArray(cosObjectInput) 将文件全部加载到内存中！
            // 如果下载的是几百 MB 甚至 GB 级别的文件，全部塞进内存会直接撑爆 JVM 导致 OOM（内存溢出）。
            // 使用 IOUtils.copy (或 Spring 的 StreamUtils.copy) 可以实现流式搬运，边读边写，内存占用极小。
            IOUtils.copy(cosObjectInput, response.getOutputStream());

            // 5. 刷新输出流，确保底层缓冲区的数据全部发送给客户端
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("file download error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载失败");
        } finally {
            // 6. 【关键】无论下载成功还是失败，都必须在 finally 块中释放网络流
            // 原因：COSObjectInputStream 底层占用着 HTTP 连接，如果不主动关闭，会导致客户端的连接池迅速被耗尽，最终无法再向 COS 发起任何请求。
            if (cosObjectInput != null) {
                cosObjectInput.close();
            }
        }
    }

}
