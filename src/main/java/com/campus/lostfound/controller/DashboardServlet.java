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
  if(!WebUtil.loggedIn(req)){
   resp.sendRedirect(req.getContextPath()+"/login");
   return;
  }

  String uid=WebUtil.userId(req);
  String action=req.getParameter("action");
  WorkflowStore ws=WorkflowStore.get();

  if("read".equals(action)){
   ws.markRead(uid,req.getParameter("notificationId"));
  } else if("recover".equals(action)){
   DataStore ds=DataStore.get();

   try{
    String itemId=req.getParameter("itemId");
    Item item=ds.findItem(itemId);

    if(item==null
       || !uid.equals(item.getReporterId())
       || !"LOST".equals(item.getType())
       || !"OPEN".equals(item.getStatus())){
     throw new IllegalArgumentException(
       "This report cannot be marked as recovered."
     );
    }

    ds.setItemStatus(itemId,"RECOVERED");
    ws.notifyUser(uid,
      "You marked your lost item as recovered: "+item.getTitle(),
      "/dashboard");
    ws.audit(uid,"ITEM_RECOVERED",
      item.getTitle()+" ["+itemId+"]");

    req.getSession().setAttribute(
      "flashSuccess","Your lost item has been marked as recovered."
    );
   }catch(IllegalArgumentException e){
    req.getSession().setAttribute("flashError",e.getMessage());
   }
  }

  resp.sendRedirect(req.getContextPath()+"/dashboard");
 }
}

