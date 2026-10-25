package com.campus.lostfound.controller;
import jakarta.servlet.*;import jakarta.servlet.annotation.WebFilter;import jakarta.servlet.http.HttpServletResponse;import java.io.IOException;
@WebFilter("/*")
public class SecurityHeadersFilter implements Filter {
 public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException{
  if(response instanceof HttpServletResponse r){r.setHeader("X-Content-Type-Options","nosniff");r.setHeader("X-Frame-Options","DENY");r.setHeader("Referrer-Policy","strict-origin-when-cross-origin");r.setHeader("Permissions-Policy","camera=(), microphone=(), geolocation=()");r.setHeader("Content-Security-Policy","default-src 'self'; img-src 'self' blob: data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'self'");}
  chain.doFilter(request,response);
 }
}
