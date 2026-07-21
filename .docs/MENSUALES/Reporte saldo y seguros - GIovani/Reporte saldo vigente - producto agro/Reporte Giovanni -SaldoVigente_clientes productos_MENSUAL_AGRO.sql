use storage;					
go					
					
declare @fec date='2026-06-30',@fec_ant date='2026-05-31'				
---PRIMERA PARTE INICIO---					
;with cte_a as (					
select  B.*,EP03.RDESPROD 				
					
from storage.com_act.hcda001 B							
left join storage.[com_act].RETP001 EP01            					
 on EP01.RCODMOD=B.HCODMOD and EP01.RTIPOPE=B.HTIPOPE            					
 left join storage.[com_act].RETP002 EP02            					
 on EP02.RCODMOD=B.HCODMOD and EP02.RTIPOPE=B.HTIPOPE and EP02.RSUBTIP=B.HSUBTIP            					
 left join storage.[com_act].RETP003 EP03 --select * from storage.[com_act].RETP003            					
 on EP03.RCODPROD=isnull(EP02.RCODPROD,EP01.RCODPROD)      				
 WHERE B.HFECPRO=@fec					
 )					
 select * into   #Data from cte_a  WHERE RDESPROD='AGROPECUARIO'  					
   					
;with cte_a as (					
select  B.*,EP03.RDESPROD 				
					
from storage.com_act.hcda001 B					
left join storage.[com_act].RETP001 EP01            					
 on EP01.RCODMOD=B.HCODMOD and EP01.RTIPOPE=B.HTIPOPE            					
 left join storage.[com_act].RETP002 EP02            					
 on EP02.RCODMOD=B.HCODMOD and EP02.RTIPOPE=B.HTIPOPE and EP02.RSUBTIP=B.HSUBTIP            					
 left join storage.[com_act].RETP003 EP03 --select * from storage.[com_act].RETP003            					
 on EP03.RCODPROD=isnull(EP02.RCODPROD,EP01.RCODPROD)      				
 WHERE B.HFECPRO=@fec_ant					
 )					
 select * into #Data_ante from     cte_a  WHERE RDESPROD='AGROPECUARIO'  		
    		
					
				
;WITH  					
 cte_b as (					
 SELECT HASEOPER,HSALCAPMN  - HSALVENMN  HSALVIGENTE 				
 FROM #Data WHERE RDESPROD='AGROPECUARIO' 					
 and hindcar='VIGENTE'					
 )		,			
 cte_c as (					
 select a.*,B.RDESGRU,b.RDESTER,RDESCOR,RDESUNI,RDESSEC  from cte_b a				
 LEFT join (select * from storage.ref.wjercor03 where rfecpro=@fec and rindfec='actual')b					
 on a.haseoper=b.rcodsec					
 )					
 select @fec fecha,RDESGRU grupo,RDESTER territorio,RDESCOR corredor,RDESUNI unidad,RDESSEC sectorista,sum(HSALVIGENTE) Saldo_Vigente   from  cte_c  group by RDESGRU,RDESTER,RDESCOR,RDESUNI,RDESSEC
 
--INICIAL	
;WITH  					
 cte_b as (					
 SELECT HASEOPER,HSALCAPMN  - HSALVENMN  HSALVIGENTE 				
 FROM #Data_ante WHERE RDESPROD='AGROPECUARIO' 					
 and hindcar='VIGENTE'					
 )		,			
 cte_c as (					
 select a.*,B.RDESGRU,b.RDESTER,RDESCOR,RDESUNI,RDESSEC  from cte_b a				
 LEFT join (select * from storage.ref.FJERCOR02(@fec_ant))b					
 on a.haseoper=b.rcodsec					
 )					
 select  @fec_ant fecha,RDESGRU grupo,RDESTER territorio,RDESCOR corredor,RDESUNI unidad,RDESSEC sectorista,sum(HSALVIGENTE) saldo_vigente 
 from  cte_c where rdesgru is not null group by RDESGRU,RDESTER,RDESCOR,RDESUNI,RDESSEC
 
--FIN PRIMERA PARTE
					
  					
----------------------------------------					
					
--select hfecpro	haseoper	concat_ws('-'	hnumdoc	htipdoc	hpais) codigoper from #Data
--select  hfecpro	haseoper	concat_ws('-'	hnumdoc	htipdoc	hpais) codigoper  from  #Data_ante
	declare @fec date='2026-06-30',@fec_ant date='2026-05-31'				
				
		
 ;with cte_cierre as(					
 select hfecpro,haseoper,count(distinct concat_ws('-',hnumdoc,htipdoc,hpais)) codigoper 
 from #Data --ENERO					
 group by hfecpro,haseoper				
 )		,			
 cte_cierre_mes as(					
 select hfecpro,haseoper,count(distinct concat_ws('-',hnumdoc,htipdoc,hpais)) codigoper 
 from  #Data_ante --DICIEMBRE					
 group by hfecpro,haseoper				
 ),					
 CTE_D AS (					
 select a.hfecpro,a.haseoper,isnull(b.codigoper,0) CierreMesAnterior,isnull(a.codigoper,0)  CierreActual,
 isnull(b.codigoper,0) - isnull(a.codigoper,0)  LOGICA			
 from cte_cierre a 					
 full join cte_cierre_mes b					
 on a.haseoper=b.haseoper					
 )					
 SELECT A.*,B.RDESGRU,b.RDESTER,RDESCOR,RDESUNI 
 into #R --drop table #R
 FROM CTE_D A					
 left JOIN (select * from storage.ref.wjercor03 where rfecpro=@fec and rindfec='actual')b					
 ON A.HASEOPER=B.RCODSEC					

select hfecpro fecha,RDESGRU grupo,RDESTER territorio,RDESCOR corredor,RDESUNI unidad,haseoper sectorista,CierreMesAnterior,CierreActual,LOGICA 
from #R where RDESGRU is not null order by 1 asc

 

--select hfecpro,RDESGRU,RDESTER,RDESCOR,RDESUNI,sum(logica)
--from #r
--group by hfecpro,RDESGRU,RDESTER,RDESCOR,RDESUNI
--order by 1 asc