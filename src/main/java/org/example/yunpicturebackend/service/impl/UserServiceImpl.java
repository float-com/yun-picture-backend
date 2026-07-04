package org.example.yunpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.yunpicturebackend.model.dto.space.SpaceAddRequest;
import org.example.yunpicturebackend.model.dto.user.UserLoginRequest;
import org.example.yunpicturebackend.model.dto.user.UserQueryRequest;
import org.example.yunpicturebackend.model.dto.user.UserRegisterRequest;
import org.example.yunpicturebackend.exception.BusinessException;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.enums.SpaceLevelEnum;
import org.example.yunpicturebackend.model.enums.UserRoleEnum;
import org.example.yunpicturebackend.model.vo.LoginUserVO;
import org.example.yunpicturebackend.model.vo.UserVO;
import org.example.yunpicturebackend.service.SpaceService;
import org.example.yunpicturebackend.service.UserService;
import org.example.yunpicturebackend.mapper.UserMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.example.yunpicturebackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户注册服务实现类
 * * @Service: 这是 Spring 框架的核心注解之一。
 * 它告诉 Spring Boot：“这是一个业务逻辑类，请你在项目启动时自动把它实例化，并放到 Spring 的大容器（IoC 容器）里管理”。
 * 这样其他地方（比如 Controller）就可以直接通过 @Resource 或 @Autowired 把它拿来用，而不需要每次都 new UserServiceImpl()。
 */
@Slf4j
@Service
/*
 * ServiceImpl<UserMapper, User>: 这是 MyBatis-Plus 框架提供的“超级父类”。
 * 传统的开发中，我们需要自己写增删改查的代码。继承了这个类之后，MyBatis-Plus 会利用泛型（传入了 UserMapper 和 User 实体），
 * 在底层自动帮我们生成并实现单表的 CRUD（增删改查）方法。
 * 这就是为什么我们在代码下面可以直接调用 this.save(user) 和 this.baseMapper 的原因。
 */
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private SpaceService spaceService;

    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest) {
        // ==================== 1. 防御性拦截 ====================
        // 在商业项目中，永远不要相信前端传来的数据。如果传入的对象本身是 null，
        // 后续调用 userRegisterRequest.getUserAccount() 会直接引发服务器内部错误（NPE，空指针异常）。
        if (userRegisterRequest == null) {
            // BusinessException 是我们自定义的全局业务异常类。
            // 抛出这个异常后，Spring 的全局异常处理器（GlobalExceptionHandler）会将其捕获，
            // 并转换为标准的 JSON 格式（包含错误码和错误信息）返回给前端。
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();

        // ==================== 2. 字段合规性校验 ====================
        // StrUtil 是 Hutool 工具包提供的方法，hasBlank 会检查传入的任意参数是否为 null、空字符串"" 或纯空白字符"   "。
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短，应不少于4位");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短，应不少于8位");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }

        // ==================== 3. 账号唯一性校验（与数据库交互） ====================
        /*
         * QueryWrapper: MyBatis-Plus 提供的“条件构造器”。
         * 它的作用是用面向对象（写 Java 代码）的方式，动态拼接 SQL 语句中的 WHERE 条件。
         * 下面这行代码相当于拼装了 SQL: WHERE userAccount = '前端传来的账号'
         */
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);

        /*
         * this.baseMapper: ServiceImpl 父类中提供的一个属性，代表了底层的 UserMapper。
         * selectCount: 执行 SELECT COUNT(*) FROM user WHERE userAccount = '...'
         * 这种写法免去了我们手写 SQL 的繁琐。
         */
        long count = this.baseMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }

        // ==================== 4. 密码安全处理 ====================
        // 调用本类底部的私有方法，将明文密码（如 12345678）转换为密文。
        String encryptPassword = getEncryptPassword(userPassword);

        // ==================== 5. 构建实体并持久化（入库） ====================
        // 实例化一个与数据库表结构一一映射的 User 实体类
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名"); // 给予默认昵称

        // 从枚举类中获取“普通用户”的实际值（通常是 "user" 或 0），保证数据一致性，避免手写字符串引发拼写错误。
        user.setUserRole(UserRoleEnum.USER.getValue());

        /*
         * this.save(user): 这是继承 ServiceImpl 后白嫖来的方法。
         * 底层会自动生成类似 INSERT INTO user (userAccount, userPassword, ...) VALUES (...) 的 SQL 并执行。
         * 返回 boolean 类型代表是否插入成功。
         */
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }

        // ==================== 6. 注册后自动开通默认空间（容错增强） ====================
        /*
         * 业务场景：用户注册成功后，系统尽量自动为其创建一个“普通版默认空间”，让新用户进入系统后可以直接上传和管理图片。
         * 兜底策略：空间创建属于注册后的配套初始化能力，不应反向影响“账号已成功创建”这个主流程。
         * 因此这里采用 try-catch 包裹：如果数据库短暂抖动、并发防重命中或空间模块异常，只记录错误日志，用户后续仍可通过 /space/add 手动创建空间。
         */
        try {
            SpaceAddRequest spaceAddRequest = new SpaceAddRequest();
            spaceAddRequest.setSpaceName("默认空间");
            spaceAddRequest.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
            spaceService.addSpace(spaceAddRequest, user);
        } catch (Exception e) {
            log.error("create default space failed after user register, userId = {}", user.getId(), e);
        }

        // 插入成功后，MyBatis-Plus 会自动将数据库生成的自增 ID 赋值回 user 对象的 id 属性中。
        // 因此这里可以直接 return user.getId()。
        return user.getId();
    }

    /*
    * 用户登录服务实现类
    * */
    @Override
    public String getEncryptPassword(String userPassword) {
        /*
         * 盐值（Salt）的作用：
         * 如果用户密码是 "123456"，直接 MD5 加密后的密文是固定的。黑客可以通过“彩虹表（一张巨大明文密文对照表）”反查出原密码。
         * 我们在密码前拼接一段随机或固定的复杂字符串（盐值 float_com），
         * 实际加密的是 "float_com123456"，这样生成的密文就完全改变了，极大提升了破解难度。
         */
        final String SALT = "float_com";

        /*
         * DigestUtils: Spring 框架自带的摘要算法工具类。
         * md5DigestAsHex: 将拼接后的字节数组进行 MD5 计算，并转换成人类可读的 32 位十六进制字符串返回。
         */
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    @Override
    public LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        // ==================== 1. 防御性拦截 ====================
        // 同样的，永远不要相信前端的传参。防止 DTO 本身为 null 导致的系统级空指针异常（NPE）。
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        // 从 DTO 中安全提取字段
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

        // ==================== 2. 字段合规性校验 ====================
        // 使用 Hutool 工具类快速排查 null、空字符串或纯空格
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码不能为空");
        }

        /*
         * 【安全提示】防用户枚举攻击：
         * 在登录接口中，即使是账号长度不够，我们也尽量统一定义为“用户不存在或密码错误”，或者含糊地提示“账号或密码错误”。
         * 不要明确告诉调用者“账号长度错误”或“密码错误”，防止恶意攻击者利用接口报错信息探测系统中存在哪些真实账号。
         */
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }

        // ==================== 3. 密码加密验证 ====================
        // 数据库中存储的是加密后的盐值密码，因此必须将用户传入的明文密码用同样的算法加密一次，再去数据库比对密文。
        String encryptPassword = getEncryptPassword(userPassword);

        // ==================== 4. 核心查询（与数据库交互） ====================
        /*
         * QueryWrapper 动态拼接 SQL 语句：
         * 相当于执行 SELECT * FROM user WHERE userAccount = '...' AND userPassword = '...'
         */
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);

        /*
         * selectOne: MyBatis-Plus 提供的方法，预期只会查出一条记录（因为我们在注册时保证了账号的唯一性）。
         * 如果查不到（账号错误，或密码匹配不上），会返回 null。
         */
        User user = this.baseMapper.selectOne(queryWrapper);

        // 匹配失败
        if (user == null) {
            // 在后台留下日志，方便排查恶意爆破，但对前端依然返回模糊的提示(小技巧：这里尽量简短且使用英文)
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }

        // ==================== 5. 记录用户登录态 (Session) ====================
        /*
         * 登录成功的核心标志就是向服务端的 Session 空间中写入当前用户的信息。
         * 底层原理：Tomcat 服务器会自动在响应头中给浏览器种下一个名为 JSESSIONID 的 Cookie。
         * 之后前端每一次发起请求，都会自动携带这个 Cookie，后端就能借此识别出是哪个用户在操作了。
         */
        request.getSession().setAttribute(USER_LOGIN_STATE, user);

        // ==================== 6. 数据脱敏与返回 ====================
        /*
         * 严禁将刚从数据库查出来的 User 实体（包含了加密密码、角色等）直接返回。
         * 调用本类的 getLoginUserVO 方法，将 User 转换为 LoginUserVO 进行脱敏，只返回前端需要的基础信息。
         */
        return this.getLoginUserVO(user);
    }

    /**
     * 获取当前登录用户（服务端内部流转专用）
     * <p>
     * 【架构设计】状态检索与数据实时性的权衡：
     * 1. 凭证验证：通过 Tomcat 维护的 Session 机制，获取当前请求上下文中的登录凭证。
     * 2. 数据保鲜策略：Session 中缓存的用户信息可能会出现“滞后”（例如：用户在另一个页面刚修改了昵称，或者其账号刚刚被管理员封禁/修改权限，此时 Session 里依然是旧数据）。
     * 因此，本方法默认采取“Session 校验凭证 + DB 获取最新状态”的严谨策略，以确保后续高阶业务逻辑（如权限拦截、发帖等）所依赖的数据是绝对准确的。
     *
     * @param request Tomcat 注入的原生 HTTP 请求上下文
     * @return 数据库中最新、最完整的用户信息实体 (User)
     * @throws BusinessException 若未登录、登录已过期或账号被意外删除，抛出全局异常阻断请求
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // ==================== 1. 凭证校验（读取 Session 缓存） ====================
        // 尝试从当前会话中获取预先存入的登录态对象（对应 userLogin 接口中的 setAttribute 操作）
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;

        // 防御性拦截：如果 Session 已经失效被清空，或者因脏数据导致对象没有 ID，统统视为未鉴权状态
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录或登录已过期");
        }

        // ==================== 2. 数据保鲜（回源数据库查询） ====================
        /*
         * 【性能提示与进阶演进】
         * 这里根据 Session 里的 userId 重新去数据库查了一次。
         * - 优点：保证了数据的绝对实时性（如：封禁状态、权限变更秒级生效）。
         * - 缺点：高频调用时（比如每个接口都要走一遍），会给数据库带来不小的查询压力。
         *
         * 演进方案：
         * 1. 追求极限性能：如果业务允许一定的延迟，或者根本没有“封禁”这种严格诉求，可以注释掉下方查库代码，直接 return currentUser。
         * 2. 企业级架构方案：引入 Redis 分布式缓存来替代本地 Session，将高频访问的用户最新状态放入 Redis 进行统一管理。
         */
        long userId = currentUser.getId();

        // this.getById 是 MyBatis-Plus ServiceImpl 提供的方法，直接根据主键查询
        currentUser = this.getById(userId);

        // 二次拦截：防范极端并发边缘场景（例如：用户处于登录状态时，其底层数据库记录被管理员物理删除了）
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "账号状态异常，请重新登录");
        }

        return currentUser;
    }

    /**
     * 获取脱敏后的登录用户信息 (实体类转 VO)
     * <p>
     * 【设计原理】基于 Bean 属性拷贝的隐式脱敏：
     * 这里利用了 Hutool 的 BeanUtil 工具类，它底层通过反射将 User 实体中与 LoginUserVO 同名的属性进行赋值。
     * 由于我们在设计 LoginUserVO 时，刻意去掉了 userPassword、salt 等敏感字段，
     * 这些危险字段在拷贝过程中会因为“找不到目标坑位”而被自然丢弃，从而用极其精简的代码实现了数据的安全脱敏。
     *
     * @param user 刚从数据库中查询出来、包含全部敏感信息的 User 实体对象
     * @return 经过安全脱敏、仅保留前端展示所需字段的 LoginUserVO 视图对象
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    /**
     * 用户注销（退出登录）核心业务逻辑
     * <p>
     * 【架构设计】服务端状态擦除：
     * 注销的核心在于打破“无状态 HTTP”中维系用户身份的桥梁。通过抹除服务端内存（Session）里的凭证数据，
     * 确保客户端即使在本地保留了旧的 JSESSIONID Cookie，也无法再凭此调用后续的敏感接口。
     *
     * @param request Tomcat 注入的原生 HTTP 请求上下文，用于精准操作当前用户的 Session
     * @return 注销成功返回 true
     * @throws BusinessException 严格模式下，若当前上下文根本不存在登录态，则抛出异常
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // ==================== 1. 登录状态前置校验 ====================
        // 尝试获取当前会话中的登录凭证标识
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);

        /*
         * 【设计思考】关于退出接口的幂等性（Idempotency）：
         * 当前代码的逻辑比较严格：如果用户没登录却调用了注销，直接抛异常报错。这种设计适用于强状态管理的后台系统。
         * 但在一些高容错的 C 端互联网产品中，为了追求极致的接口幂等性，
         * 遇到 userObj == null 时往往也会直接 `return true`（不抛异常，假装注销成功），
         * 避免用户狂点注销按钮导致页面弹出无意义的报错红条。
         */
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录或登录已过期");
        }

        // ==================== 2. 执行状态擦除（核心注销操作） ====================
        /*
         * 【安全提示】removeAttribute vs invalidate:
         * 1. removeAttribute(KEY): 温和策略。仅从 Session 字典中精准移除代表“身份”的键值对。
         * 优点是不会破坏用户的“游客体验”（例如：即便退出了账号，Session 里存的未登录状态下的购物车商品依然存在）。
         * 2. invalidate(): 毁灭策略。直接炸毁当前用户的整个 Session 空间。
         * 优点是最为彻底和安全，防止任何潜在的 Session 遗留数据泄露。
         * * 当前系统采用了温和的 removeAttribute 策略。
         */
        request.getSession().removeAttribute(USER_LOGIN_STATE);

        return true;
    }

    /**
     * 获得脱敏后的用户信息
     * <p>
     * 【设计原理】
     * 这是核心的数据脱敏处理节点。通过将数据库实体类 (Entity) 的属性复制到视图类 (VO) 中，
     * 由于 VO 中本身没有定义 password、salt 等敏感字段，所以在拷贝过程中这些敏感数据自然就被丢弃过滤了。
     *
     * @param user 数据库中的完整用户实体对象
     * @return 经过脱敏和格式化处理的 UserVO 对象；如果传入的对象为 null，则安全返回 null
     */
    @Override
    public UserVO getUserVO(User user) {
        // 1. 防御性编程：判空拦截，防止后续调用引发 NullPointerException
        if (user == null) {
            return null;
        }

        // 2. 初始化目标视图对象
        UserVO userVO = new UserVO();

        // 3. 属性拷贝：使用框架提供的工具类（如 Spring 的 BeanUtils）
        // 它会通过反射机制，将源对象 (user) 中与目标对象 (userVO) 同名同类型的属性值浅拷贝过去。
        BeanUtils.copyProperties(user, userVO);

        return userVO;
    }

    /**
     * 获得脱敏后的用户信息列表
     * <p>
     * 【业务场景】
     * 极常用于“分页查询用户列表”或“批量获取用户信息”的接口中。
     * * @param userList 数据库查询出来的完整用户实体集合
     * @return 转换后的 UserVO 集合；若入参为空，则默认返回空集合，避免前端接收到 null 时崩溃
     */
    @Override
    public List<UserVO> getUserVoList(List<User> userList) {
        // 1. 防御性拦截：借助 Hutool 工具类判断集合是否为 null 或 size == 0
        if (CollUtil.isEmpty(userList)) {
            // 良好的 API 实践：返回一个空的 ArrayList 而不是 null，对前端解析更加友好
            return new ArrayList<>();
        }

        // 2. 核心转换逻辑：利用 Java 8 Stream API 进行链式处理
        return userList.stream()
                // .map(): 数据映射。遍历流中的每一个 User 对象，将其作为参数传递给本类的 getUserVO 方法。
                // (this::getUserVo) 是方法引用的简写，等同于 user -> this.getUserVO(user)
                .map(this::getUserVO)
                // .collect(): 将流中处理完毕的所有 UserVO 对象重新收集并打包成一个 List 集合
                .collect(Collectors.toList());
    }






    /**
     * 获取用户查询条件封装器 (QueryWrapper) 具体实现
     * <p>
     * 【设计原理】
     * 基于 MyBatis Plus 提供的 QueryWrapper 实现按需动态拼接 SQL。
     * 针对不同类型的字段采用严格分类的匹配策略：
     * 1. 精确匹配 (eq)：主键 (id)、状态/枚举值 (userRole)。
     * 2. 模糊匹配 (like)：用户输入的文本类信息 (userAccount, userName, userProfile)。
     * 3. 动态排序 (orderBy)：根据前端指定的字段和顺序 (升序/降序) 进行排序。
     *
     * @param userQueryRequest 包含各项筛选条件的请求参数
     * @return 组装完毕的 QueryWrapper 对象
     * @throws BusinessException 若请求参数为空，触发防御性拦截并抛出异常
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        // 1. 防御性编程：防止上游调用方传入 null 导致后续的空指针异常
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        // 2. 参数解构：将 DTO 中的查询条件提取出来
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        // 3. 初始化 QueryWrapper
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();

        // 4. 动态 SQL 拼接：第一个参数为 true 时，该条件才会加入到最终的 SQL 中
        // 【精确查询】主键和角色属于强关联标识，必须精确匹配 (SQL: id = ? AND userRole = ?)
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);

        // 【模糊查询】文本类字段支持关键字搜索 (SQL: userAccount LIKE '%?%')
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);

        // 5. 排序规则拼接
        // 判断 sortField 是否存在，若存在则根据 sortOrder 决定是 ASC 还是 DESC
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);

        return queryWrapper;
    }


    @Override
    public boolean isAdmin(User user) {
        // 判空保护，并校验用户的角色属性是否与系统预设的管理员枚举值一致
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }


}



