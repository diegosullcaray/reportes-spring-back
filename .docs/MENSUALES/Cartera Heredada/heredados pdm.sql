--select * into #MrvGrupoPDM from dma.dbo.MrvGrupoPDM
;with a as (
select *
from dma.dbo.FecCieBt
--where RFCIEBT between eomonth('20250201',-23) and '20250531'
--where RFCIEBT between '20250201' and '20250531'
where RFCIEBT='20260630'
),
cte_b as (
select * from dma.dbo.HisCreditos where hfecpro in(select RFCIEBT from a) 
)
select A.HFECPRO,A.BCODOPE
,B.BCODMOD,B.BTIPOPE,B.BSUBTIP
,F.BCODUBT ---HFECPRO,BCODOPE,BCODMOD,BTIPOPE,BSUBTIP,BCODUBT,RSECOPE,BNUMGRU
,G.RSECOPE
,H.BNUMGRU
,E.RCODPROD
--,I.SNOMG
into #A1  --drop table #A1
from cte_b A ---select hfecpro,count(*) from dma.dbo.HisCreditos where hfecpro in('20251031','20251130')  group by hfecpro
left join dwh.dbo.BREGMOD001 B
on A.BIDMOD=B.BIDMOD
left join dwh.dbo.RTIPCRE001 C
on B.BCODMOD=C.RCODMOD and B.BTIPOPE=C.RTIPOPE
left join dwh.dbo.RTIPCRE002 D
on B.BCODMOD=D.RCODMOD and B.BTIPOPE=D.RTIPOPE and B.BSUBTIP=D.RSUBTIP
left join dwh.dbo.RTIPCRE003 E
on E.RCODPROD=isnull(D.RCODPROD,C.RCODPROD)
left join dwh.dbo.BREGUBT001 F
on F.BIDUBT=A.BCODSEC
left join dwh.dbo.RREGOPE001 G
on G.RCODOPE=A.BCODOPE
left join dma.dbo.HisGruposPDM H ---select hfecpro, count(*) from dma.dbo.HisGruposPDM  where hfecpro in('20251031','20251130') group by hfecpro
on H.BCODOPE=A.BCODOPE and A.HFECPRO=H.HFECPRO

select h.HFECPRO,h.BCODOPE,h.BCODMOD,h.BTIPOPE,h.BSUBTIP,h.BCODUBT,h.RSECOPE,h.BNUMGRU,I.SNOMG 
into #A
from #A1 H
left join dma.dbo.MrvGrupoPDM I --select * from  dma.dbo.MrvGrupoPDM 
on I.BNUMGRU=H.BNUMGRU
where --A.HFECPRO in (select RFCIEBT from a) and
h.RCODPROD=6
order by 1,2


select *,
iif(isnull(BCODUBT,'')=isnull(RSECOPE,''),0,1) HFLAG 
from #A 
order by 1,2

drop table #A,#A1
--select *
--from dma.dbo.HisGruposPDM
--where BCODOPE=8730671 order by 1

