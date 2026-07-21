use storage;
go
declare @fech date ='20260630'

select top 2 RFEC,ROW_NUMBER()over(order by rfec desc) ord 
into #fec  --select* from #fec  update #fec set rfec='20250927' where ord=1  update #fec set rfec='20250830' where ord=2
from storage.ref.RCALEN001 
where RFEC<=@fech and RCIEBT=1 order by RFEC desc

declare @mesAnterior date=(select rfec from #fec where ord=2)
declare @fecActual date=(select rfec from #fec where ord=1)
------------------------- 
IF OBJECT_ID('tempdb..#cart1') IS NOT NULL DROP TABLE #cart1
create table #cart1( 
HCODOPE int,
HTIPOPE smallint,
HNUMDOC varchar(25),
HNUMCLI varchar(50),
HASEOPER varchar(12),
HFECPRO date, 
HSALCAPMN numeric (17,2), 
HSALVENMN numeric (17,2), 
HINDCAR varchar(25), 
HCODORI varchar(12),
HCODDES varchar(12),
HASEINC varchar(12),
ORD smallint )

--tabla traslados
select * into #traslado  --drop table #traslado,#cartera,#CART
from storage.com_act.HCTC001  ---select distinct hfecpro from storage.com_act.HCTC001 order by 1 desc
where HFECPRO in(@fecActual)


--cartera
select HCODOPE,HTIPOPE,HNUMDOC, concat_ws('-',HNUMDOC,HTIPDOC,HPAIS) HNUMCLI,  
HASEOPER,A.HFECPRO, 
HSALCAPMN, HSALVENMN, HINDCAR,hfecdes 
into #cartera
from storage.com_act.HCDA001 A 
where A.HFECPRO in(select rfec from #fec)
 
select b.*, 
 a.HCODORI,
  a.HCODDES,a.HINDDESEM 
into #CART  
from #cartera b   
left join #traslado a   ---select * from #traslado
on    b.HCODOPE=a.HCODOPE and b.HTIPOPE=a.HTIPOPE and b.HFECPRO=a.HFECPRO
where b.hfecpro=@fecActual




select HASEOPER,SUM(CASE WHEN HINDCAR = 'VIGENTE' THEN HSALCAPMN - HSALVENMN ELSE 0 END)HSALVIGENTE 
into #VIGMES_ACT  --DROP TABLE #VIGMES_ACT,#VIGMES_ANTE
from #cartera
where HFECPRO=@fecActual
group by HASEOPER

select HASEOPER,SUM(CASE WHEN HINDCAR = 'VIGENTE' THEN HSALCAPMN - HSALVENMN ELSE 0 END)HSALVIGENTE 
into #VIGMES_ANTE
from #cartera
where HFECPRO=@mesAnterior
group by HASEOPER


select  isnull(A.HASEOPER,B.HASEOPER) HASEOPER, isnull(B.HSALVIGENTE,0)-isnull(A.HSALVIGENTE,0) HVARSALVIGMN,
CAST(NULL AS FLOAT) HSALVIGENTEORI,CAST(NULL AS FLOAT) HSALVIGENTEDES, 0 HNUMOPEORI,0 HNUMOPEDES,0 HNUMOPEDESE
into #B  --DROP TABLE #B
from #VIGMES_ANTE a
full join #VIGMES_ACT b
on isnull(A.HASEOPER,'N/A')=isnull(B.HASEOPER,'N/A') 
 
select hcodori,isnull(SUM(CASE WHEN HINDCAR = 'VIGENTE' THEN HSALCAPMN - HSALVENMN ELSE 0 END),0) HSALVIGENTEORI,count(distinct case when  HINDDESEM=1 then HCODOPE else null end) ope_ori
into #Ori
from #CART    ---SELECT * FROM #CART
group by hcodori


select HCODDES,isnull(SUM(CASE WHEN HINDCAR = 'VIGENTE' THEN HSALCAPMN - HSALVENMN ELSE 0 END),0) HSALVIGENTEDES ,count(distinct case when  HINDDESEM=1 then HCODOPE else null end) ope_des
into #des
from #CART   
group by HCODDES
  
UPDATE A
SET A.HSALVIGENTEORI=B.HSALVIGENTEORI,A.HNUMOPEORI=B.ope_ori
FROM #B A  --SELECT * FROM #B
INNER JOIN #Ori B
ON A.haseoper=B.hcodori

UPDATE A
SET A.HSALVIGENTEDES=B.HSALVIGENTEDES,A.HNUMOPEDES=B.ope_des
FROM #B A
INNER JOIN #des B
ON A.haseoper=B.HCODDES

UPDATE A
SET A.HNUMOPEDESE=B.HNUMOPE
FROM #B A
INNER JOIN (SELECT HASEOPER,COUNT(DISTINCT HCODOPE)HNUMOPE FROM storage.com_act.HCMA001  WHERE HFECPRO=@fecActual GROUP BY HASEOPER) B
ON A.haseoper=B.HASEOPER
 
;WITH CTE_A AS (
SELECT A.*, HVARSALVIGMN -  ISNULL(HSALVIGENTEDES,0) + ISNULL(HSALVIGENTEORI,0)  VAR_VIGENTE ,
HNUMOPEDESE - HNUMOPEDES + HNUMOPEORI PRODUCTIVDAD ,C.RatioRecuperacion RatioRecuperacion0_30, B.RatioRecuperacion RatioRecuperacion1_30
FROM #B A   --SELECT * FROM #B
LEFT JOIN (SELECT scodsec, CASE 
            WHEN SUM(ISNULL(SSALINIMN, 0)) = 0 THEN 0 
            ELSE SUM(ISNULL(SSALRECMN, 0) + ISNULL(SSALMAN01MN, 0)) / SUM(SSALINIMN)
        END AS RatioRecuperacion
    FROM storage.[com_act].[SDAE003]  --select max(sfecpro) from storage.[com_act].[SDAE003] where scodagr=6
    WHERE sfecpro = @fecActual AND scodagr = 3 
	GROUP BY scodsec --EXEC staging.[sig].[PTDSLK113] '20260630'
	) B --SELECT MAX(HFECPRO) FROM storage.com_act.HCMA001  WHERE HFECPRO='20260630' group by HASEOPER
ON A.HASEOPER=B.scodsec
--exec storage.[com_act].[CWWSALCAPCOMER01] '2026-06-30' --SELECT MAX(HFECPRO) FROM storage.com_act.hcda001
--exec storage.[com_act].[CWWSALCAPCOMER01] '20260630'
LEFT JOIN (SELECT scodsec, CASE 
            WHEN SUM(ISNULL(SSALINIMN, 0)) = 0 THEN 0 
            ELSE SUM(ISNULL(SSALRECMN, 0) + ISNULL(SSALMAN01MN, 0)) / SUM(SSALINIMN)
        END AS RatioRecuperacion
    FROM storage.[com_act].[SDAE002]  --select max(sfecpro) from storage.[com_act].[SDAE003] where scodagr=6
    WHERE sfecpro = @fecActual AND scodagr = 3 
	GROUP BY scodsec
	) C
ON A.HASEOPER=C.scodsec
)
SELECT HASEOPER,VAR_VIGENTE,PRODUCTIVDAD,RatioRecuperacion0_30,RatioRecuperacion1_30 
FROM CTE_A
WHERE HASEOPER NOT IN('','--')
 

 