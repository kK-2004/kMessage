package com.kk2004.kmessage.channel;

import com.kk2004.common.exception.BusinessException;
import com.kk2004.kmessage.domain.ChannelType;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ChannelAdapterRegistry {
    private final Map<ChannelType, ChannelAdapter> adapters = new EnumMap<>(ChannelType.class);
    public ChannelAdapterRegistry(List<ChannelAdapter> adapters) {
        adapters.forEach(a -> this.adapters.put(a.type(), a));
    }
    public ChannelAdapter require(ChannelType type) {
        if (!type.implemented()) throw new BusinessException("邮箱渠道尚未实现");
        ChannelAdapter adapter = adapters.get(type);
        if (adapter == null) throw new BusinessException("渠道适配器不可用: " + type);
        return adapter;
    }
}
