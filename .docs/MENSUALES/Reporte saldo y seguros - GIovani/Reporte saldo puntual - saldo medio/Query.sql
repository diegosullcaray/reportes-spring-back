 

use storage;
go
declare @date date='2026-06-30'
/*
select * from storage.[com_pas].[sdps010] 
where SFECPRO='20230831' and SCODAGR=8
*/
;with cte_a as (
select * from storage.[com_pas].[sdps013] 
where SFECPRO=@date and SCODAGR=8
), 
cte_aa as (
select p.RDESCPROD02,b.* 
from cte_a b
left join storage.[com_pas].[RETP001] P     --select * from storage.[com_pas].[RETP001]                     
on P.RCODMOD=B.SSBMOD and P.RTIPOPE=B.SSBTOPE
)  ,
CTE_BB AS (
SELECT RDESCPROD02,SSUCCLI,SUM(SSALMN)SSALMN  
FROM cte_aa  
GROUP BY RDESCPROD02,SSUCCLI
)
,
CTE_B AS ( 
select   DISTINCT b.RCODAGEH, b.RDESAGEH  RDESAGEH, a.RDESCPROD02, a.SSALMN   ,ISNULL(b.RDESMAT,'sin asignar')RDESMAT,
isnull(b.RDESMAC,'sin asignar')RDESMAC,isnull(b.RDESTER ,'sin asignar')RDESTER
from CTE_BB a
LEFT join  storage.ref.VJERCOR04 b  --select * from  storage.ref.VJERCOR04 where rcodage=1
on a.SSUCCLI=b.RCODAGE   
)
SELECT * FROM CTE_B 

  use storage;
go
declare @date date='20260630'

--Saldo Medio
;with cte_a as (
select * from storage.com_pas.wjas004 where   HTIPCOD=4 AND HFECPRO in(@date)
)
select distinct a.hfecpro,b.RDESAGEH,a.RDESCPROD02,a.HSALMEDMN
from cte_a a
inner join storage.ref.VJERCOR04 b
on a.HCODREL=b.RCODAGEH
order by a.HFECPRO asc
 