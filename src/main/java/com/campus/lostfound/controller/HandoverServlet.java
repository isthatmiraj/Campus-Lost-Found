package com.campus.lostfound.controller;
import com.campus.lostfound.model.*;import com.campus.lostfound.util.*;import jakarta.servlet.ServletException;import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.IOException;
@WebServlet(urlPatterns="/handover")
public class HandoverServlet extends HttpServlet{
 protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws ServletException,IOException{
  if(!WebUtil.loggedIn(req)){resp.sendRedirect(req.getContextPath()+"/login");return;}
  String uid=WebUtil.userId(req),itemId=req.getParameter("itemId"); DataStore ds=DataStore.get(); WorkflowStore ws=WorkflowStore.get();
  try{Item item=ds.findItem(itemId);if(item==null)throw new IllegalArgumentException("Item not found.");Claim approved=null;for(Claim c:ds.allClaims())if(c.getItemId().equals(itemId)&&"APPROVED".equals(c.getStatus())){approved=c;break;}
   if(approved==null)throw new IllegalArgumentException("An approved claim is required before confirming a return.");
   if(!uid.equals(item.getReporterId())&&!uid.equals(approved.getClaimantId()))throw new IllegalArgumentException("Only the reporter and approved claimant can confirm this return.");
   ws.createHandover(itemId,item.getReporterId(),approved.getClaimantId());String status=ws.confirmHandover(itemId,uid);
   if("RETURNED".equals(status)){ds.setItemStatus(itemId,"RETURNED");ws.notifyUser(item.getReporterId(),"Both parties confirmed the item was returned: "+item.getTitle(),"/dashboard");ws.notifyUser(approved.getClaimantId(),"Both parties confirmed the item was returned: "+item.getTitle(),"/dashboard");ws.audit(uid,"RETURN_CONFIRMED",item.getTitle()+" ["+itemId+"]");}
   else{ws.notifyUser(uid.equals(item.getReporterId())?approved.getClaimantId():item.getReporterId(),"The other party confirmed the return for: "+item.getTitle(),"/dashboard");ws.audit(uid,"RETURN_CONFIRMATION",item.getTitle()+" ["+itemId+"]");}
   resp.sendRedirect(req.getContextPath()+"/dashboard?handover=1");
  }catch(IllegalArgumentException e){req.getSession().setAttribute("flashError",e.getMessage());resp.sendRedirect(req.getContextPath()+"/dashboard");}
 }
}
