package org.example.yunpicturebackend.service;

import org.example.yunpicturebackend.model.dto.UserLoginRequest;
import org.example.yunpicturebackend.model.dto.UserRegisterRequest;
import org.example.yunpicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.yunpicturebackend.model.vo.LoginUserVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author 24042
* @description 针对表【user(系统用户表)】的数据库操作Service
* @createDate 2026-06-01 20:20:41
*/
public interface UserService extends IService<User> {
    /**
     * 用户注册
     * <p>
     * 接收并处理前端传入的注册表单数据，完成账号唯一性校验及密码加密入库。
     * </p>
     *
     * @param userRegisterRequest 用户注册请求参数封装对象 (DTO)
     * @return 注册成功后，返回系统为该新用户分配的全局唯一 ID
     */
    long userRegister(UserRegisterRequest userRegisterRequest);


    /**
     * 获取加密后的密码
     *
     * @param userPassword 原始密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);


    /**
     * 用户登录
     * <p>
     * 【架构设计】闭环的登录处理流程：
     * 1. 接收 DTO (UserLoginRequest)：严格限制前端仅能传入账号和密码，隔离实体类，防止批量赋值漏洞。
     * 2. 返回 VO (LoginUserVO)：将底层 User 实体转换为 VO 返回，剔除密码、盐值等敏感信息，保障接口数据的绝对安全。
     *
     * @param userLoginRequest 包含用户账号和密码的登录请求封装类 (DTO)
     * @param request          HTTP 请求上下文对象，用于操作 Session 以记录当前用户的登录状态
     * @return 登录成功后，返回经过脱敏处理的用户信息视图对象 (VO)
     */
    LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 获取当前登录用户（服务端内部上下文）
     * <p>
     * 【架构设计】状态检索与内外隔离：
     * 1. 状态检索：利用客户端发送的 HTTP 请求（底层自动携带 JSESSIONID Cookie），从服务端的 Session 内存中提取出此前存入的登录态记录。
     * 2. 为什么这里返回完整的 User 实体而不是 LoginUserVO？
     * - 内部依赖：此方法绝大多数情况下是为后端“内部其他业务”服务的（例如：插入一条图片记录时，需要获取当前登录人的 id 作为创建者；或者进行高危操作时，需要获取最真实的 role 进行权限断言）。
     * - 职责隔离：Service 层内部运转应该使用最完整的基础实体（Entity）。如果某个 Controller 接口确实需要把当前登录人的信息返回给前端展示，应由 Controller 负责调用此方法后，再将其转换为 VO。
     *
     * @param request Tomcat 注入的原生 HTTP 请求上下文对象，用于读取包含登录凭证的 Session
     * @return 当前会话上下文中已登录的完整用户信息实体 (User)
     */
    User getLoginUser(HttpServletRequest request);


    /**
     * 获取脱敏后的登录用户信息
     * <p>
     * 【设计原理】实体对象 (Entity) 与视图对象 (VO) 的隔离边界：
     * 数据库映射对象（User）通常包含密码、加密盐值、逻辑删除标志等高危敏感字段，严禁将其直接透传到前端。
     * 此方法充当了系统内外交互的“安全滤网”，负责将内部流转的 User 实体转化为仅包含安全展示字段的 LoginUserVO。
     * 这种 Entity -> VO 的强制转换，是企业级开发中保障数据隐私和系统安全的基础规范。
     *
     * @param user 包含完整数据库记录的底层用户实体对象
     * @return 剔除敏感信息后，专供前端展现的登录用户视图对象 (VO)
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 用户退出登录（注销）
     * <p>
     * 【架构设计】安全退出与服务端会话销毁：
     * 在完整的鉴权闭环中，注销绝对不能仅仅是“前端清空一下本地的缓存或 Token”。
     * 服务端必须主动介入，利用传入的 request 对象找到对应的 Session，并将其强制移除或销毁（Invalidate）。
     * 这样即使恶意攻击者此前窃取了用户的 Cookie (JSESSIONID)，一旦用户正常注销，该旧凭证在服务端也会瞬间成为废纸，从而阻断会话劫持风险。
     *
     * @param request Tomcat 注入的原生 HTTP 请求上下文对象，用于精准定位并清除当前用户的 Session 登录态
     * @return 注销操作是否成功（通常只要没有发生系统异常，都应返回 true，且设计上建议保持幂等性，即多次点击注销不报错）
     */
    boolean userLogout(HttpServletRequest request);
}
