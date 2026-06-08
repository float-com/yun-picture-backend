package org.example.yunpicturebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.exception.ThrowUtils;
import org.example.yunpicturebackend.manager.FileManager;
import org.example.yunpicturebackend.model.dto.file.UploadPictureResult;
import org.example.yunpicturebackend.model.dto.picture.PictureUploadRequest;
import org.example.yunpicturebackend.model.entity.Picture;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.vo.PictureVO;
import org.example.yunpicturebackend.service.PictureService;
import org.example.yunpicturebackend.mapper.PictureMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 图片信息服务实现类 (Service Implementation)
 * <p>
 * 职责：处理与图片相关的核心业务逻辑。继承自 MyBatis-Plus 的 ServiceImpl，默认拥有基础的 CRUD 能力。
 * @author 24042
 * @description 针对表【picture(图片信息表)】的数据库操作Service实现
 * @createDate 2026-06-08 19:33:54
 */
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService{

    @Resource
    private FileManager fileManager;

    /**
     * 上传图片（统一处理新增和更新逻辑）
     * <p>
     * 业务流程：
     * 1. 权限校验：确保用户已登录。
     * 2. 更新校验：若携带图片 ID，则防御性校验该记录在数据库中是否存在。
     * 3. 物理上传：调用底层的 FileManager 将文件安全上传至 COS，并按用户 ID 隔离存储目录。
     * 4. 数据装配：提取云端返回的元数据（宽高、大小、格式等）组装数据库实体。
     * 5. 持久化：利用 saveOrUpdate 特性，根据 ID 的有无，自动执行 INSERT 或 UPDATE。
     *
     * @param multipartFile        前端传入的物理图片文件对象
     * @param pictureUploadRequest 图片上传扩展参数（核心用于携带图片 id，区分新增或更新）
     * @param loginUser            当前已认证的登录用户对象
     * @return PictureVO           上传并成功入库后，返回给前端的脱敏视图对象
     */
    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 1. 基础安全拦截：强制要求必须登录后才能执行上传，防范匿名用户恶意传图消耗云端流量和存储
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 2. 提取更新标识（判断当前操作是“全新上传”还是“对旧记录的物理文件替换”）
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }

        // 3. 更新操作的防御性编程
        if (pictureId != null) {
            // 如果是更新动作，必须查库校验待更新的图片记录是否真实存在。
            // 避免前端传递虚假 ID 导致后续业务出现脏数据或空指针异常。
            boolean exists = this.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .exists();
            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 注意：严格的生产环境中，这里可能还需要进一步校验该 pictureId 是否归属于当前 loginUser.getId()，防止水平越权（修改别人的图片）
        }

        // 4. 动态构造云端对象存储的目录前缀
        // 策略：按照业务线 (public) + 用户 ID (loginUser.getId()) 划分外层目录
        // 优势：实现不同用户间的文件物理隔离，不仅方便排查问题，也有利于后续针对单个用户进行空间配额统计或违规资源清理
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());

        // 5. 调度底层文件管理器，完成向第三方 COS 的安全上传与图片元数据（CI）解析
        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);

        // 6. 数据搬运与装配：将上传成功后的 DTO 结果转换为数据库底层能识别的 Entity 实体
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setName(uploadPictureResult.getPicName());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        // 绑定图片归属权，记录数据拥有者
        picture.setUserId(loginUser.getId());

        // 7. 处理“更新操作”的特有字段逻辑
        if (pictureId != null) {
            // 补充主键 ID（MyBatis-Plus 识别到有主键时会触发 UPDATE 而不是 INSERT）
            picture.setId(pictureId);
            // 强制刷新业务层面的手动编辑时间
            picture.setEditTime(new Date());
        }

        // 8. 统一持久化入库
        // 机制：底层会自动判断 picture 对象的 id 是否为空，若为空执行 insert，非空则执行 updateById
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");

        // 9. 将入库成功的实体对象转换为剔除了敏感字段的 VO 对象返回给前端渲染
        return PictureVO.objToVo(picture);
    }

}
