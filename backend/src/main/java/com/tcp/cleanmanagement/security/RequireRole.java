package com.tcp.cleanmanagement.security;

import com.tcp.cleanmanagement.enums.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 클래스/메서드에 접근 가능한 역할을 지정한다.
 * 메서드에 선언된 값이 클래스 선언보다 우선한다.
 * 선언이 없으면 "로그인한 사용자 누구나" 접근 가능하다.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    Role[] value();
}
