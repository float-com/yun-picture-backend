package org.example.yunpicturebackend.service;

import org.example.yunpicturebackend.model.dto.picture.PictureUploadRequest;
import org.example.yunpicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

/**
* @author 24042
* @description 针对表【picture(图片信息表)】的数据库操作Service
* @createDate 2026-06-08 19:33:54
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片（统一处理新增和更新逻辑）
     * <p>
     * 业务流程：
     * 1. 接收前端传递的物理文件流和请求参数。
     * 2. 根据 pictureUploadRequest 中的 id 判断当前操作是“全新上传”还是“更新替换旧图”。
     * 3. 调度底层的 FileManager 将文件安全上传至 COS，并解析获取图片的宽高、体积等元数据。
     * 4. 组装 Picture 实体类数据，并绑定当前登录用户的归属信息。
     * 5. 执行数据库的插入或更新操作，最终返回脱敏后的视图对象 (VO) 供前端渲染。
     *
     * @param multipartFile        前端传入的物理图片文件对象（Spring MVC 自动封装）
     * @param pictureUploadRequest 图片上传扩展请求参数（核心用于携带图片 id：若为 null 代表新增上传；若有值代表对已有记录进行物理文件替换）
     * @param loginUser            当前已认证的登录用户对象（用于绑定图片归属权 userId，记录操作人，保证数据安全和溯源）
     * @return PictureVO           图片上传并成功入库后，返回给前端的图片视图对象（已剔除底层敏感字段，并包含完整的图片展示信息）
     */
    PictureVO uploadPicture(MultipartFile multipartFile,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

}
