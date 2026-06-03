package org.example.yunpicturebackend.controller;

import org.example.yunpicturebackend.common.BaseResponse;
import org.example.yunpicturebackend.common.ResultUtils;
import org.example.yunpicturebackend.model.dto.UserLoginRequest;
import org.example.yunpicturebackend.model.dto.UserRegisterRequest;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.exception.ThrowUtils;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.vo.LoginUserVO;
import org.example.yunpicturebackend.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 用户接口控制器 (表现层)
 * <p>
 * 职责边界：该类仅负责接收前端发起的 HTTP 请求、进行基础参数校验、
 * 路由分发给对应的 Service 层处理，并最终将处理结果封装为标准 JSON 格式响应给前端。
 * 绝对不在此处编写诸如数据库操作、密码加密等核心业务逻辑。
 * </p>
 *
 * @RestController: 这是一个组合注解（@Controller + @ResponseBody）。
 * - @Controller: 告诉 Spring 容器，这是一个 Web 控制器类，需要被 IoC 容器管理。
 * - @ResponseBody: 告诉 Spring MVC，该类中所有方法的返回值，都不要当作 HTML 视图解析（不进行页面跳转），
 * 而是直接序列化为 JSON 格式写入到 HTTP 响应体（Response Body）中。
 */
@RestController
/*
 * @RequestMapping: 定义该控制器下所有接口的统一基础路由（Base URL）。
 * 前端请求路径必须以 "/user" 开头才能进入该类，例如：http://localhost:8080/api/user/...
 */
@RequestMapping("/user")
public class UserController {

    /*
     * @Resource: 依赖注入（Dependency Injection, DI）注解。
     * 在传统的 Java 编程中，我们需要手动 new UserServiceImpl() 才能使用其方法。
     * 但在 Spring 框架中，对象的创建和销毁由 Spring IoC（控制反转）容器统一管理。
     * 打上 @Resource 或 @Autowired 注解后，Spring 会自动在内存中找到 UserService 的实现类，
     * 并将其“注入”到这个变量中。这极大地降低了类与类之间的耦合度。
     */
    @Resource
    private UserService userService;

    /**
     * 用户注册接口
     *
     * @PostMapping: RESTful 风格的路由注解，等同于 @RequestMapping(value = "/register", method = RequestMethod.POST)。
     * 为什么注册要用 POST？
     * 1. 语义上：POST 用于向服务器提交数据并创建新资源。
     * 2. 安全上：POST 请求的参数包含在请求体中，不会像 GET 请求那样暴露在 URL 地址栏里（尤其包含密码时）。
     *
     * @param userRegisterRequest 用户注册请求参数
     * @return 注册成功后的用户 ID，被包装在统一的泛型响应体 BaseResponse 中
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(
            /*
             * @RequestBody: 核心反序列化注解。
             * 前端通过 Axios/Fetch 发送过来的是一串 JSON 格式的字符串（存在于 HTTP 请求体中）。
             * Spring 框架底层的 HttpMessageConverter（通常是 Jackson 库）看到这个注解后，
             * 会自动将 JSON 字符串解析、映射并转换为 Java 的 UserRegisterRequest 对象。
             */
            @RequestBody UserRegisterRequest userRegisterRequest) {

        // 1. 防御性拦截：确保前端传来的 JSON 载荷不为空
        // 此处利用自定义的 ThrowUtils 工具类实现断言逻辑。如果不满足条件，直接抛出业务异常，阻断后续流程。
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 业务透传：将完整的数据传输对象 (DTO) 丢给业务逻辑层 (Service) 进行深度处理
        // 控制器不需要关心具体的注册细节（如怎么查重、怎么加密），只关心返回的结果。
        long result = userService.userRegister(userRegisterRequest);

        // 3. 结果封装：将裸露的数据（如 123456L）包装为前后端约定的标准格式
        // 标准格式通常包含：状态码 (code)、提示信息 (message) 和实际数据 (data)。
        return ResultUtils.success(result);
    }

    /**
     * 用户登录接口
     *
     * @PostMapping: RESTful 风格的路由注解，将 HTTP POST 请求映射到该方法。
     * 为什么登录也要用 POST 而不是 GET？
     * 1. 语义上：虽然登录听起来像是“获取”当前用户信息，但在 REST 架构视角下，登录本质上是“创建”了一个新的会话凭证（Session 或 Token）资源。
     * 2. 安全上：GET 请求会将账号密码等敏感参数明文拼接到 URL 地址栏中，极易被路由器抓包、网关日志或浏览器历史记录泄露。使用 POST 可以将敏感信息包裹在请求体（Body）中进行加密传输（配合 HTTPS）。
     *
     * @param userLoginRequest 包含前端传入账号密码的登录请求对象 (DTO)
     * @param request          Tomcat 注入的原生 HTTP 请求上下文对象，核心作用是用来记录登录态
     * @return 登录成功后，返回经过脱敏处理的用户信息 (VO)，并包装在统一响应体 BaseResponse 中
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(
            /*
             * @RequestBody: 核心反序列化注解。
             * 拦截前端发来的 JSON 格式请求体，并由 Spring 底层的 HttpMessageConverter（如 Jackson）
             * 自动将其映射、装配为 Java 内部的 UserLoginRequest 数据传输对象。
             */
            @RequestBody UserLoginRequest userLoginRequest,
            /*
             * 架构考量：为什么这里要额外引入原生的 HttpServletRequest？
             * 因为 HTTP 协议本身是“无状态”的（服务器处理完单次请求就会忘记客户端）。
             * 为了让系统在后续的接口访问中“记住”当前是哪个用户在操作，我们需要用到 Session 机制，
             * 而 Session 的获取与操作强依赖于这个原生的 request 对象。
             */
            HttpServletRequest request) {

        // 1. 防御性拦截：确保前端传来的 JSON 载荷不为空
        // 如果 DTO 为 null，后续在 Service 层调用 userLoginRequest.getUserAccount() 时会引发灾难性的 NPE（空指针异常）。
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 业务透传与状态写入：将 DTO 和 request 上下文一并交由业务逻辑层 (Service) 处理
        // Controller 层作为大堂经理，不亲自处理“比对密码”的脏活累活。
        // Service 层不仅会负责账号密码的校验，校验成功后还会利用传入的 request 将该用户信息登记到服务端的 Session 空间中。
        LoginUserVO loginUserVO = userService.userLogin(userLoginRequest, request);

        // 3. 结果封装与返回：将安全、脱敏的视图数据 (VO) 包装为前后端约定的标准格式返回
        // 只有经过脱敏的 VO 才能流出 Controller 层，严禁直接返回带有密码的实体类对象。
        return ResultUtils.success(loginUserVO);
    }


    /**
     * 获取当前登录用户信息接口
     * <p>
     * 【架构设计】Controller 层的标准编排逻辑：
     * Controller 层不应包含复杂的业务运算，只负责请求的接收与流程的调度：
     * 1. 状态获取：调用 Service 获取当前上下文中的完整用户实体（若未登录，Service 内部会直接抛出全局异常，此处流程自动阻断）。
     * 2. 视图转换：严格执行“内外隔离”的安全规范，将携带敏感信息的 User 实体转换为专供前端展示的 LoginUserVO。
     * 3. 统一响应：利用 ResultUtils 统一包装返回值，确保前端拿到的永远是 {code, data, message} 标准结构。
     *
     * @param request Tomcat 注入的原生 HTTP 请求上下文，用于向 Service 层传递 Session 凭证
     * @return 包含当前登录用户脱敏信息 (VO) 的标准响应体
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        // 1. 获取完整的当前登录用户实体（内部流转）
        User loginUser = userService.getLoginUser(request);

        // 2. 将实体转换为安全的视图对象，并包装为全局统一响应格式返回
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }


    /**
     * 用户注销（退出登录）接口
     *
     * @PostMapping: 为什么注销操作强烈建议使用 POST 而不是 GET？
     * 1. RESTful 语义规范：GET 请求应当是“安全且幂等”的（即仅仅获取数据，不改变服务器状态）。而注销操作实质上是销毁了服务端的 Session 凭证，改变了服务器状态，因此在语义上应使用 POST（或 DELETE）。
     * 2. 浏览器“预加载”防范（核心踩坑点）：现代浏览器或某些页面加速插件，为了提升用户体验，可能会静默对页面中的 GET 链接发起“预加载（Prefetch）”。如果你的注销接口是 GET，用户可能只是在页面上随便逛逛，就被浏览器后台发出的预加载请求莫名其妙地踢下线了。
     *
     * @param request Tomcat 注入的原生 HTTP 请求上下文，必须依赖它去寻找并销毁对应用户的 Session
     * @return 包含布尔值结果的标准响应体，true 代表注销成功
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {

        // 1. 极限防御性拦截
        // 虽然在正常的 Spring Web 环境下，request 是绝对不可能为 null 的，
        // 但在某些特殊的单元测试环境、或是被第三方框架异常代理拦截时可能会出现丢失。加上断言彰显了极致的严谨性。
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);

        // 2. 业务透传与调度
        // Controller 层恪守“调度者”本分，绝不在此处直接写 request.getSession().removeAttribute(...)。
        // 抹除会话的具体实现细节全部委托给 Service 层处理。
        boolean result = userService.userLogout(request);

        // 3. 统一响应封装
        // 将 Service 返回的裸布尔值（true/false），包装成前后端约定的标准结构 {code: 0, data: true, message: "ok"} 返回。
        return ResultUtils.success(result);
    }



}