package org.example.yunpicturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.yunpicturebackend.annotation.AuthCheck;
import org.example.yunpicturebackend.common.BaseResponse;
import org.example.yunpicturebackend.common.DeleteRequest;
import org.example.yunpicturebackend.common.ResultUtils;
import org.example.yunpicturebackend.constant.UserConstant;
import org.example.yunpicturebackend.exception.BusinessException;
import org.example.yunpicturebackend.model.dto.user.*;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.exception.ThrowUtils;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.vo.LoginUserVO;
import org.example.yunpicturebackend.model.vo.UserVO;
import org.example.yunpicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

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


    /*
     * =======================================================================================
     * 【架构设计说明】为什么以下基础 CRUD 接口没有将逻辑抽离到 Service 层自定义方法中？
     *
     * 在传统的经典三层架构中，通常要求 Controller 只负责接收请求，所有逻辑必须下沉到 Service 层。
     * 但在现代敏捷开发（尤其是深度集成 MyBatis Plus 框架）的工程实践中，我们采用了更务实的策略（Pragmatic CRUD）：
     *
     * 1. 拒绝冗余的“套娃”代码：对于单表、单次的简单操作（如直接根据 ID 更新/删除），如果强行抽离到 Service 层，
     * 往往只会产生毫无业务增量的透传代码（Controller 传给 Service，Service 直接调底层），徒增维护成本。
     * 2. 充分利用 IService 通用能力：MyBatis Plus 的 IService 本身已经充当了通用业务层，提供了完善的
     * save、removeById、updateById、page 等方法。对于简单操作，Controller 完成参数校验和转换后直接调用即可。
     * 3. 严格的边界划分：只有面临“多表关联、强事务控制、复杂的业务规则编排”（例如：用户注册时的查重与密码加盐逻辑）时，
     * 才会严格将其封装到 Service 层的自定义业务方法中。
     * =======================================================================================
     */

    /**
     * 创建用户（仅管理员）
     * <p>
     * 【业务场景】后台管理系统中，由管理员手动录入新用户。
     *
     * @param userAddRequest 用户创建请求参数（包含账号、角色等基本信息）
     * @return 新建用户的 ID
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        // 1. 防御性拦截：确保请求体不为空
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 数据转换 (DTO -> Entity)：将前端传入的参数拷贝到数据库实体中
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);

        // 3. 业务规则处理：为后台手动创建的用户统一设置默认密码，并进行加密
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);

        // 4. 持久化：调用 MyBatis Plus 提供的通用 save 方法写入数据库
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(user.getId());
    }

    /**
     * 根据 ID 获取用户完整信息（仅管理员）
     * <p>
     * 【注意】此接口返回的是 User 实体类，包含密码盐值等极度敏感信息，因此必须限制为管理员调用。
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 直接调用 MyBatis Plus 底层方法查询
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 ID 获取脱敏后的用户视图对象 (VO)
     * <p>
     * 【业务场景】通常用于前端展示某个用户的公开主页或基本信息。
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        // 【⚠️ Spring AOP 陷阱预警】
        // 此处直接调用了同类中的 getUserById(id) 方法。
        // 由于是内部调用 (this.getUserById)，Spring AOP 代理会失效！
        // 这意味着 getUserById 上的 @AuthCheck(mustRole = "admin") 注解在这里【不会生效】。
        // 因此，普通用户也能通过这个接口拿到结果（这在此处刚好符合取 VO 脱敏数据的公开需求，但属于歪打正着，建议留意）。
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();

        // 返回前进行数据脱敏
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 删除用户（仅管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 调用 MyBatis Plus 逻辑删除 / 物理删除
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户信息（仅管理员）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);

        // 调用 MyBatis Plus 提供的通用 updateById，它会动态判断：只有非空的字段才会更新
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(true);
    }

    /**
     * 分页获取用户脱敏列表（仅管理员）
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);

        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();

        // 1. 查出数据库中的原始分页对象（包含完整的 User 实体）
        Page<User> userPage = userService.page(
                new Page<>(current, pageSize),
                userService.getQueryWrapper(userQueryRequest)
        );

        // 2. 初始化目标 VO 分页对象，将分页元数据（当前页、页大小、总条数）拷贝过去
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());

        // 3. 将分页对象中的 Records（实体列表）批量转换为 VO 列表
        List<UserVO> userVOList = userService.getUserVoList(userPage.getRecords());

        // 4. 将脱敏后的列表塞入新的分页对象中返回
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }


}