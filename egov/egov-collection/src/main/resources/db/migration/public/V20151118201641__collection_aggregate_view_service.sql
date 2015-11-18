drop view public.receipt_aggr_view ;
create or replace view public.receipt_aggr_view
as 
select date(receiptdate) as receipt_date,service.code as service,  text 'public' as ulb, sum(Totalamount) as total,count(*) as recordCount 
from	public.egcl_collectionheader header,public.egcl_servicedetails service	where  header.servicedetails=service.id and status in
(select id from	public.egw_status where moduletype='ReceiptHeader' and code in ('TO_BE_SUBMITTED','SUBMITTED','APPROVED','REMITTED'))
group by receipt_date ,service
