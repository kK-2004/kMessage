package com.kk2004.kmessage.domain;

public enum ChannelType {
    TELEGRAM("Telegram", "Bot Token（如 123456789:ABC-DEF1234ghIkl-zyx57W2v1u123ew11）",
            "通过 Bot Token 向聊天发送消息。",
            "1. 在 Telegram 中向 @BotFather 发送 /newbot 创建机器人\n2. 按提示设置名称和用户名后获取 Bot Token\n3. 将 Bot Token 直接填入凭据栏\n4. 目标使用聊天 chat id（群聊为负数），或在 Bot 已加入的公开频道下使用 @channelusername\n5. 首次发送前目标需先主动给 Bot 发送消息，Bot 才能取得 chat id",
            "聊天 chat id（用户为正数，群聊为负数）或 @channelusername"),
    FEISHU("飞书", "App ID:App Secret（如 cli_xxxxxxxx:xxxxxxxx）",
            "通过飞书企业自建应用机器人向用户或群组发送消息。",
            "1. 在飞书开放平台创建企业自建应用（https://open.feishu.cn/app）\n2. 在「凭证与基础信息」页获取 App ID 和 App Secret\n3. 在「权限管理」开通：im:message:send_as_bot（发送消息）、im:chat:readonly（列出群组）、contact:user.id:readonly（通过手机号/邮箱获取用户 ID）、contact:user.employee_id:readonly（获取用户 user ID）、contact:user.phone:readonly（手机号查询）、contact:user.email:readonly（邮箱查询）、contact:department.base:readonly（获取部门信息）、contact:user.base:readonly（获取用户基本信息）\n4. 发布应用版本并由管理员审批，在「通讯录权限范围」设为「全部成员」才能拉取整个组织架构\n5. 将 App ID 和 App Secret 以 app_id:app_secret 格式直接填入凭据栏\n6. target 使用 open_id（ou_ 开头）、user_id、union_id（on_ 开头）、chat_id（oc_ 开头）或 email；群聊需先把机器人加入群组\n7. 手机号批量导入：中国大陆号可不带区号（自动补 +86），国际号需带 + 国家码",
            "open_id（ou_ 开头）/ user_id / union_id（on_ 开头）/ chat_id（oc_ 开头）/ email"),
    EMAIL("邮箱", "", "通过 SMTP 发送邮件（暂未实现）。", "", "收件人邮箱地址");

    private final String label;
    private final String credentialHint;
    private final String description;
    private final String setupGuide;
    private final String targetHint;

    ChannelType(String label, String credentialHint, String description, String setupGuide, String targetHint) {
        this.label = label;
        this.credentialHint = credentialHint;
        this.description = description;
        this.setupGuide = setupGuide;
        this.targetHint = targetHint;
    }

    public String getLabel() { return label; }
    public String getCredentialHint() { return credentialHint; }
    public String getDescription() { return description; }
    public String getSetupGuide() { return setupGuide; }
    public String getTargetHint() { return targetHint; }

    /** An adapter implementation exists for this channel type. */
    public boolean implemented() {
        return this != EMAIL;
    }
}
