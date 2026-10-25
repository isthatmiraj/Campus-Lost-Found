package com.campus.lostfound.controller;

import com.campus.lostfound.model.*;
import com.campus.lostfound.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

@WebServlet(urlPatterns={"/dashboard","/notifications"})
public class DashboardServlet extends HttpServlet {
 protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws ServletException,IOException{
  if(!WebUtil.loggedIn(req)){resp.sendRedirect(req.getContextPath()+"/login");return;}
  String uid=WebUtil.userId(req); DataStore ds=DataStore.get(); WorkflowStore ws=WorkflowStore.get();
  List<Item> all=ds.allItems(); List<Item> mine=new ArrayList<>(); for(Item i:all)if(i.getReporterId().equals(uid))mine.add(i);
  req.setAttribute("myItems",mine); req.setAttribute("myClaims",ds.claimsForUser(uid)); req.setAttribute("notifications",ws.notificationsFor(uid)); req.setAttribute("handovers",ws.handoversFor(uid)); req.setAttribute("unread",ws.unreadCount(uid));
  req.getRequestDispatcher("/WEB-INF/views/dashboard.jsp").forward(req,resp);
 }
 protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws ServletException,IOException{
  if(!WebUtil.loggedIn(req)){resp.sendRedirect(req.getContextPath()+"/login");return;}
  String action=req.getParameter("action"); WorkflowStore ws=WorkflowStore.get();
  if("read".equals(action)){ws.markRead(WebUtil.userId(req),req.getParameter("notificationId"));}
  resp.sendRedirect(req.getContextPath()+"/dashboard");
 }
}
