package org.example.yunpicturebackend.event;

/**
 * 图片分页缓存清理事件
 * <p>
 * 业务场景：空间模块删除空间时会连带删除空间内图片，但不能反向注入 PictureService，否则会与 PictureServiceImpl -> SpaceService 形成循环依赖。
 * 设计说明：通过轻量事件通知图片模块清理缓存，既保持模块解耦，也避免开启 Spring 循环依赖兜底配置。
 */
public class PictureCacheClearEvent {
}
