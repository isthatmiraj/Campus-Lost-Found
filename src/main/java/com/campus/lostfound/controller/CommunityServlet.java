package com.campus.lostfound.controller;

import com.campus.lostfound.model.Item;
import com.campus.lostfound.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

@WebServlet(urlPatterns={"/item","/saved-items","/flag"})
public class CommunityServlet extends HttpServlet {
 protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws ServletException,IOException{
  String path=req.getServletPath();
  if("/item".equals(path)){String id=req.getParameter("id");Item item=(id==null)?null:DataStore.get().findItem(id);if(item==null){resp.sendError(404,"Listing not found");return;}req.setAttribute("item",item);req.setAttribute("reporter",DataStore.get().findUserById(item.getReporterId()));req.setAttribute("saved",WebUtil.loggedIn(req)&&CommunityStore.get().isSaved(WebUtil.userId(req),item.getId()));req.getRequestDispatcher("/WEB-INF/views/item-detail.jsp").forward(req,resp);return;}
  if(!WebUtil.loggedIn(req)){resp.sendRedirect(req.getContextPath()+"/login");return;}
  List<Item> items=new ArrayList<>();for(String id:CommunityStore.get().savedIds(WebUtil.userId(req))){Item i=DataStore.get().findItem(id);if(i!=null)items.add(i);}req.setAttribute("savedItems",items);req.getRequestDispatcher("/WEB-INF/views/saved-items.jsp").forward(req,resp);
 }
 protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws ServletException,IOException{
  if(!WebUtil.loggedIn(req)){resp.sendRedirect(req.getContextPath()+"/login");return;}String path=req.getServletPath(),uid=WebUtil.userId(req),ctx=req.getContextPath();String id=req.getParameter("itemId");Item item=id==null?null:DataStore.get().findItem(id);if(item==null){resp.sendError(404,"Listing not found");return;}
  try{if("/item".equals(path)){CommunityStore.get().toggleSaved(uid,id);WorkflowStore.get().audit(uid,"BOOKMARK_TOGGLED","Item "+id);resp.sendRedirect(ctx+"/item?id="+java.net.URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8));return;}
   if("/flag".equals(path)){String reason=req.getParameter("reason");if(reason==null||!Set.of("Spam","Incorrect information","Inappropriate content","Already returned","Other").contains(reason))throw new IllegalArgumentException("Choose a valid reason.");if(uid.equals(item.getReporterId()))throw new IllegalArgumentException("You cannot flag your own listing.");CommunityStore.get().flag(uid,id,reason);req.getSession().setAttribute("flashMessage","Thanks. Your report was sent to the administrators.");resp.sendRedirect(ctx+"/item?id="+java.net.URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8));return;}
  }catch(IllegalArgumentException e){req.getSession().setAttribute("flashError",e.getMessage());resp.sendRedirect(ctx+"/item?id="+java.net.URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8));return;}
  resp.sendError(404);
 }
}
