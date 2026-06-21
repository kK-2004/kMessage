package com.kk2004.kmessage.security;

import com.kk2004.kmessage.domain.Entities.Caller;
import com.kk2004.common.exception.UnauthorizedException;

public final class CallerContext {
    private static final ThreadLocal<Caller> CURRENT = new ThreadLocal<>();
    private CallerContext() {}
    public static void set(Caller caller) { CURRENT.set(caller); }
    public static Caller require() {
        Caller caller = CURRENT.get();
        if (caller == null) throw new UnauthorizedException("缺少有效调用方凭据");
        return caller;
    }
    public static void clear() { CURRENT.remove(); }
}
