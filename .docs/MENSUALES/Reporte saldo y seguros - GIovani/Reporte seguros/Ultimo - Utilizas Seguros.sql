use mod_rep;
go
declare @tip_cod int=7, @cod_rel varchar(50)='231'      
declare @dFechaMax date='20260630'--(select MAX(SFECPRO) from storage.[com_act].[SDAF002])   
 DECLARE @FecMeta date,@FecJerar date  
set @FecMeta='20260630'--(select max(hfecpro) from [storage].[com].[VDMCOM01] where HCODVAR=5001 )          
   
set @FecJerar='20260630'--(select max(rfecpro) from storage.[ref].WJERCOR03 where RINDFEC='ACTUAL' )     
    
	
/* Total Operaciones*/      
;with cte_a as (      
select b.*, EP03.RCODPROD  ,
case when     EP03.RCODPROD=1 THEN 'AGRO'
     when     EP03.RCODPROD=2 THEN 'CC'
	 when     EP03.RCODPROD=3 THEN 'CE'
	 when     EP03.RCODPROD=4 THEN 'EC' 
	 when     EP03.RCODPROD=5 THEN 'ICPYME'
	 when     EP03.RCODPROD=6 THEN 'PDM'
	 when     EP03.RCODPROD=7 THEN 'CONS'
	 when     EP03.RCODPROD=8 THEN 'GL'
	 when     EP03.RCODPROD=9 THEN 'TFC'
	 when     EP03.RCODPROD=10 THEN 'CONV'
	 when     EP03.RCODPROD=11 THEN 'HIP'
	 when     EP03.RCODPROD=12 THEN 'IC'
	 when     EP03.RCODPROD=13 THEN 'MAXI'
	 when     EP03.RCODPROD=14 THEN 'OTROS'
	 when     EP03.RCODPROD=15 THEN 'PFE'
	 when     EP03.RCODPROD=16 THEN 'CFAE'
	 when     EP03.RCODPROD=17 THEN 'REACT'
	 when     EP03.RCODPROD=18 THEN 'IO'
	 when     EP03.RCODPROD=19 THEN 'IN'
	 when     EP03.RCODPROD=20 THEN 'INCFAE'
	 when     EP03.RCODPROD=21 THEN 'NFAE'
	 END cod_s
from  storage.[com_act].[SDAF002] B      
left join storage.[com_act].RETP001 EP01      
 on EP01.RCODMOD=B.SCODMOD and EP01.RTIPOPE=B.STIPOPE      
 left join storage.[com_act].RETP002 EP02      
 on EP02.RCODMOD=B.SCODMOD and EP02.RTIPOPE=B.STIPOPE and EP02.RSUBTIP=B.SSUBTIP      
 left join storage.[com_act].RETP003 EP03 --select * from storage.[com_act].RETP003      
 on EP03.RCODPROD=isnull(EP02.RCODPROD,EP01.RCODPROD)      
WHERE SFECPRO=@dFechaMax AND SCODAGR=6      
),      
cte_b as (        
SELECT 'Cartera' Tip,null SEG,RCODPROD,cast(isnull(C.RCODSEC,'0') as varchar(250)) SCODDESCRIP ,      
     c.RDESGRU,c.RDESCOR,case when c.RDESTER='SIN ASIGNAR' THEN NULL ELSE  c.RDESTER END RDESTER ,  case when c.rdessec='NO ENCONTRADO' then null else C.RDESSEC end descripcion,SUM(A.SNUMOPE) SNUMOPE,cod_s      
FROM CTE_A A --SELECT * FROM storage.[ref].WJERCOR01      
LEFT JOIN storage.[ref].WJERCOR03 C on A.SCODSEC =  C.RCODSEC and A.SFECPRO=C.RFECPRO and RINDFEC='ACTUAL'       
AND case when @tip_cod=15 then  ISNULL(cast(c.RCODTER as nvarchar(10)),'9999')                     
     when @tip_cod=14 then cast(c.RCODCOR as nvarchar(10))                  
  when @tip_cod=13 then c.RCODGRU                        
      when @tip_cod=11 then c.RCODUNI else '1' end=IIF(@tip_cod=7,'1',@cod_rel)       
GROUP BY  RCODPROD,cast(isnull(C.RCODSEC,'0') as varchar(250)), c.RDESGRU,c.RDESCOR,c.RDESTER,     
    C.RDESSEC       ,cod_s 
),      
cte_seg as (      
select b.*, EP03.RCODPROD   ,
CASE WHEN scodseg=802 THEN 'S_PC'      
     WHEN SCODSEG in(806,815,825) THEN 'S_AG'      
     WHEN SCODSEG in(803,817) THEN 'S_MC'      
     WHEN SCODSEG in(200,220,230) THEN 'S_MR' 
     when SCODSEG in(814,828) then 'S_ONCO'     
     END       'cod_s'   
from storage.[com_seg].SDSF001 B      
left join storage.[com_act].RETP001 EP01      
 on EP01.RCODMOD=B.SCODMOD and EP01.RTIPOPE=B.STIPOPE      
 left join storage.[com_act].RETP002 EP02      
 on EP02.RCODMOD=B.SCODMOD and EP02.RTIPOPE=B.STIPOPE and EP02.RSUBTIP=B.SSUBTIP      
 left join storage.[com_act].RETP003 EP03 --select * from storage.[com_act].RETP003      
 on EP03.RCODPROD=isnull(EP02.RCODPROD,EP01.RCODPROD)      
where b.sfecpro=@dFechaMax  and scodagr=13      
),      
CTE_SEG_PC_JER as (     
SELECT 'Seguro' Tip,SCODSEG seg ,RCODPROD,cast(isnull(C.RCODSEC,'0') as varchar(250)) SCODDESCRIP , 
c.RDESGRU,c.RDESCOR,case when c.RDESTER='SIN ASIGNAR' THEN NULL ELSE  c.RDESTER END RDESTER ,     
   case when c.rdessec='NO ENCONTRADO' then null else C.RDESSEC end descripcion,SUM(A.SNUMPLZ)  SNUMPLZ ,cod_s     
FROM cte_seg A --SELECT * FROM storage.[ref].WJERCOR01      
LEFT JOIN storage.[ref].WJERCOR03 C on A.SCODSEC =  C.RCODSEC and A.SFECPRO=C.RFECPRO and RINDFEC='ACTUAL'       
AND case when @tip_cod=15 then  ISNULL(cast(c.RCODTER as nvarchar(10)),'9999')                     
         when @tip_cod=14 then cast(c.RCODCOR as nvarchar(10))                  
         when @tip_cod=13 then cast(c.RCODGRU as varchar(250))                        
         when @tip_cod=11 then cast(c.RCODUNI as varchar(250)) else '1' end=IIF(@tip_cod=7,'1',@cod_rel)       
GROUP BY  SCODSEG,RCODPROD,cast(isnull(C.RCODSEC,'0') as varchar(250)) ,c.RDESGRU,c.RDESCOR,c.RDESTER,
 C.RDESSEC ,cod_s 
),      
CTE_RESULT AS (      
SELECT *   
FROM cte_b      
UNION ALL      
SELECT *      
FROM CTE_SEG_PC_JER      
--WHERE descripcion  IS NOT NULL        
)      
select * into #Data from CTE_RESULT     --select * from #Data  


SELECT SCODDESCRIP,DESCRIPCION,  RDESGRU,RDESCOR,RDESTER,  
  AGRO,CC,CE,EC,ICPYME,PDM,CONS,GL,TFC,IO,S_PC,S_AG,S_MC,S_MR,S_ONCO
into #TABRESULTPru --DROP TABLE #TABRESULTPru   select * from #TABRESULTPru order by SCODDESCRIP  A.[18],0) + ISNULL(A.[3],0) + ISNULL(A.[7],0) + ISNULL(A.[1],0) + (isnull(A.[4],0)+isnull(A.[2],0)+isnull(A.[5],0)
FROM         
(SELECT SNUMOPE, cod_s, DESCRIPCION ,SCODDESCRIP , RDESGRU,RDESCOR,RDESTER      
FROM #Data) p   --select * from #Data    order by descripcion asc 
PIVOT        
(        
SUM (SNUMOPE)        
FOR cod_s IN        
( AGRO,CC,CE,EC,ICPYME,PDM,CONS,GL,TFC,IO,S_PC,S_AG,S_MC,S_MR,S_ONCO 

)        
) AS pvt        
ORDER BY pvt.SCODDESCRIP;     
       
	  -- select * from #Data where tip='cartera' cod_s is not null
	  --17157
	  --13617
      
SELECT TIP,SCODDESCRIP,isnull(DESCRIPCION,0) DESCRIPCION, RDESGRU,RDESCOR,RDESTER, [1],[2],[3],[4],[5],[6],[7],[8],[9],[18]      
into #TABRESULTCartera --DROP TABLE #TABRESULTCartera select * from #TABRESULTCartera      
FROM         
(SELECT SNUMOPE, RCODPROD, DESCRIPCION ,SCODDESCRIP ,TIP  , RDESGRU,RDESCOR,RDESTER    
FROM #Data       --select distinct cod_s from #Data where Tip='Cartera'
where tip='Cartera') p        
PIVOT        
(        
SUM (SNUMOPE)        
FOR RCODPROD IN        
(       
[1],[2],[3],[4],[5],[6],[7],[8],[9],[18]      
  )        
) AS pvt        
ORDER BY pvt.SCODDESCRIP;        
      
SELECT TIP,SEG,cod_s,SCODDESCRIP,DESCRIPCION, RDESGRU,RDESCOR,RDESTER, [1],[2],[3],[4],[5],[6],[7],[8],[9],[18]--[0],[1],[7],[10],[11],[15],[200],[360],[802],[803],[804],[805],[806],[807]      
into #TABRESULTSeguros --DROP TABLE #TABRESULTSeguros select * from #TABRESULTSeguros WHERE SEG=200      
FROM         
(SELECT SNUMOPE, RCODPROD, DESCRIPCION ,SCODDESCRIP ,TIP,SEG   ,cod_s, RDESGRU,RDESCOR,RDESTER   
FROM #Data --SELECT * FROM #Data where cod_s is not null 
where cod_s is not null /*tip='Seguro'*/) p        
PIVOT        
(        
SUM (SNUMOPE)        
FOR RCODPROD IN        
(       
[1],[2],[3],[4],[5],[6],[7],[8],[9],[18]--[0],[1],[7],[10],[11],[15],[200],[360],[802],[803],[804],[805],[806],[807]      
  )        
) AS pvt        
ORDER BY pvt.SCODDESCRIP;        
--select * from #TABRESULTSeguros WHERE SEG=200      
--select * from #TABRESULTCartera      
       
 ;WITH CTE_A AS (          
select *          
from [storage].[com].[VDMCOM01] --SELECT MAX(HFECPRO) FROM [storage].[com].[VDMCOM01]  WHERE    hcodvar=5001 DROP TABLE #Meta_Vr2      
where HTIPCOD='2'   and hcodvar=5001 and hfecpro=eomonth(@dFechaMax)            
)          
 ,          
CTE_B AS (          
SELECT DISTINCT   CAST(RCODSEC AS VARCHAR(250))     RCOD          
          
FROM storage.[ref].WJERCOR03 where RINDFEC='ACTUAL' AND RFECPRO=@FecJerar        
AND CASE WHEN @tip_cod='15' THEN CAST(RCODTER AS VARCHAR(250))           
         WHEN @tip_cod='14' THEN CAST(RCODCOR AS VARCHAR(250))          
         WHEN @tip_cod='13' THEN CAST(RCODGRU AS VARCHAR(250))          
         WHEN @tip_cod='11' THEN CAST(RCODUNI AS VARCHAR(250)) ELSE '1' END = IIF(@tip_cod=7,'1',@cod_rel)           
),          
CTE_C AS (          
SELECT B.RCOD HCODREL,HVALVAR          
FROM CTE_A A          
LEFT JOIN CTE_B B          
ON A.HCODREL=B.RCOD          
)  ,          
CTE_D AS (           
SELECT HCODREL,SUM(HVALVAR)  METASEG        
FROM CTE_C          
WHERE HCODREL IS NOT NULL          
GROUP BY HCODREL          
)          
SELECT * INTO #Meta_Vr2 FROM CTE_D    --select * from #Meta_Vr2   
       

select a.SCODDESCRIP,a.DESCRIPCION,a.RDESGRU,a.RDESCOR,a.RDESTER,
	   isnull(IO,0) + isnull(CE,0) + isnull(CONS,0) + isnull(AGRO,0) + isnull(EC,0) + isnull(CC,0) + isnull(ICPYME,0) as Total_Ope  ,
	   isnull(S_PC,0)+isnull(S_AG,0)+isnull(S_MC,0)+isnull(S_MR,0) + ISNULL(S_ONCO,0) as Total_Seg,
	   case when (isnull(IO,0) + isnull(CE,0) + isnull(CONS,0) + isnull(AGRO,0) + isnull(EC,0) + isnull(CC,0) + isnull(ICPYME,0))=0 then 0 else 
	   cast((isnull(S_PC,0)+isnull(S_AG,0)+isnull(S_MC,0)+isnull(S_MR,0)+ISNULL(S_ONCO,0)) as float) /  (isnull(IO,0) + isnull(CE,0) + isnull(CONS,0) + isnull(AGRO,0) + isnull(EC,0) + isnull(CC,0) + isnull(ICPYME,0)) end P_Pene_Total,
	   isnull(S_MR,0) 'Multiriesgo',isnull(S_MC,0) 'MultiCredito',isnull(S_PC,0) 'Prot_Cuota',isnull(S_AG,0) 'Agro',ISNULL(S_ONCO,0)'ONCO',
	   isnull(EC,0) EMP_CONFIA,   isnull(CC,0)  'CONST_CONFIA',isnull(S_AG,0) 'AGRO',isnull(CONS,0)  'CONSUMO',isnull(CE,0) 'CRED_EDUC',
	   isnull(IO,0) 'INI_OFICI',B.METASEG,case when B.METASEG=0 then 0 else (isnull(S_PC,0)+isnull(S_AG,0)+isnull(S_MC,0)+isnull(S_MR,0))/ B.METASEG end Avance,
	   ---- 4 , 2 5 ,
	   isnull(EC,0) + isnull(CC,0) +  isnull(ICPYME,0) as 'T_Ope_Pyme_CC',
	   c.SNUMOPE as 'T_SEG_PYME_CC' ,d.SNUMOPE as 'T_SEG_PYME_CC_MR',e.SNUMOPE as 'T_SEG_PYME_CC_MC',f.SNUMOPE as 'T_SEG_PYME_CC_PC',
	   ------
	   isnull(AGRO,0) as 'T_Ope_Agro',g.SNUMOPE  'T_SEG_AGRO_T',case when isnull(AGRO,0)=0 then 0 else cast(g.SNUMOPE as float) /isnull(AGRO,0) end AS 'PEN_AGRO',
	   h.SNUMOPE as 'AGRO_MULTIR',i.SNUMOPE as 'AGRO_MULTIC',j.SNUMOPE as 'AGRO_PC',k.SNUMOPE as 'AGRO_agro',
	   ---
	   isnull(CONS,0) as 'T_Ope_Consumo',l.SNUMOPE  'T_SEG_Consumo_T',case when isnull(CONS,0) =0 then 0 else CAST(l.SNUMOPE AS FLOAT) /isnull(CONS,0) end AS 'PEN_Consum',
	   m.SNUMOPE as 'MC_Consumo',n.SNUMOPE as 'PC_Consumo',
	   --------
	   isnull(CE,0) as 'T_Ope_CE',ñ.SNUMOPE as 'T_SEG_CE_T',case when isnull(CE,0) =0 then 0 else CAST(ñ.SNUMOPE AS FLOAT) /isnull(CE,0) end AS 'PEN_CE',
	   ---------
	   isnull(IO,0) as 'T_Ope_IO',o.SNUMOPE  'T_SEG_IO_T',case when isnull(IO,0) =0 then 0 else CAST(O.SNUMOPE AS FLOAT) /isnull(IO,0) end AS 'PEN_IO',
	   P.SNUMOPE  'mr_io',q.SNUMOPE  'mc_io',r.SNUMOPE  'pc_io' /*,
	   ----
	   KK.SNUMOPE 'T_SEG_ONCO' */

from #TABRESULTPru  A --select * from #TABRESULTPru
LEFT JOIN #Meta_Vr2 B
ON A.SCODDESCRIP=B.HCODREL
left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(4,2,5) and seg in(200,802,803) group by SCODDESCRIP/*,seg*/)c
 ON A.SCODDESCRIP=c.SCODDESCRIP
 left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(4,2,5) and seg in(200) group by SCODDESCRIP )d
 ON A.SCODDESCRIP=d.SCODDESCRIP
 left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(4,2,5) and seg in(803) group by SCODDESCRIP )e
 ON A.SCODDESCRIP=e.SCODDESCRIP
  left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(4,2,5) and seg in(802) group by SCODDESCRIP )f
 ON A.SCODDESCRIP=f.SCODDESCRIP
 left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(1) and seg in(802,200,803,806,815) group by SCODDESCRIP )g
 ON A.SCODDESCRIP=g.SCODDESCRIP
 left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(1) and seg in(200,220,230) group by SCODDESCRIP )h
 ON A.SCODDESCRIP=h.SCODDESCRIP 
 left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(1) and seg in(803) group by SCODDESCRIP )i
 ON A.SCODDESCRIP=i.SCODDESCRIP
  left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(1) and seg in(802) group by SCODDESCRIP )j
 ON A.SCODDESCRIP=j.SCODDESCRIP
  left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(1) and seg in(806,815) group by SCODDESCRIP )k
 ON A.SCODDESCRIP=k.SCODDESCRIP
   left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(7) and seg in(802,803) group by SCODDESCRIP )l
 ON A.SCODDESCRIP=l.SCODDESCRIP
    left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(7) and seg in(803) group by SCODDESCRIP )m
 ON A.SCODDESCRIP=m.SCODDESCRIP
     left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(7) and seg in(802) group by SCODDESCRIP )n
 ON A.SCODDESCRIP=n.SCODDESCRIP
      left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(3) and seg in(802) group by SCODDESCRIP )ñ
 ON A.SCODDESCRIP=ñ.SCODDESCRIP
        left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(18) and seg in(200,803,802) group by SCODDESCRIP )O
 ON A.SCODDESCRIP=O.SCODDESCRIP
        left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(18) and seg in(200,220,230) group by SCODDESCRIP )P
 ON A.SCODDESCRIP=P.SCODDESCRIP
         left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(18) and seg in(803) group by SCODDESCRIP )q
 ON A.SCODDESCRIP=q.SCODDESCRIP
    left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(18) and seg in(802) group by SCODDESCRIP )r
 ON A.SCODDESCRIP=r.SCODDESCRIP
 left join (select SCODDESCRIP,/*seg,*/sum(SNUMOPE)SNUMOPE from #Data where tip='Seguro'  and RCODPROD in(18) and seg in(814) group by SCODDESCRIP )KK
 ON A.SCODDESCRIP=KK.SCODDESCRIP
 
 
  