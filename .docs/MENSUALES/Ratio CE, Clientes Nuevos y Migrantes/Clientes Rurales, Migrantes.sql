use storage 
GO

declare @fec date='20260630'

select        RFEC 
into        #FECSCIEMES
from        storage.ref.RCALEN001 where RFEC between '20260430' and @fec and RCIEMES=1

select        RFEC 
into        #FECSCIEBT
from        storage.ref.RCALEN001 where RFEC between '20260430' and @fec and RCIEMES=1

select        A.RFEC RCIEMES,B.RFEC RCIEBT 
into        #FECS--select * from #FECS
from        #FECSCIEMES A
join        #FECSCIEBT B  --select * from #FECSCIEBT
on            eomonth(A.RFEC)=eomonth(B.RFEC)


;with cte_a as(
select        A.HFECPRO,A.HNUMDOC,A.HTIPDOC,A.HPAIS,
--case when A.HPAIS=604 then 'Peruano' else 'Migrante' end HINDMIG
CASE WHEN A.HTIPDOC in (21,9,15) THEN  'Peruano' else 'Migrante' end HINDMIG
,max(HUBIGEO)HUBIGEO
from        storage.com_act.HBCN001 A
join        #FECS AA
on            A.HFECPRO=AA.RCIEMES
join        storage.com_act.HCDA001 B
on            AA.RCIEBT=B.HFECPRO and A.HNUMDOC=B.HNUMDOC and A.HTIPDOC=B.HTIPDOC and A.HPAIS=B.HPAIS
left join    storage.com_act.RFOC001 C
on            B.HCODOPE=C.RCODOPE 
left join    (select HFECPRO,HCODOPE from storage.com_act.HCDR001 A join #FECS B on A.HFECPRO=B.RCIEBT and eomonth(HFECPRO)=eomonth(HFECREP) 
                UNION ALL 
             select HFECPRO,HCODOPE from storage.com_act.HCDR002 A join #FECS B on A.HFECPRO=B.RCIEBT and eomonth(HFECPRO)=eomonth(HFECREP)) D
on            B.HFECPRO=D.HFECPRO and B.HCODOPE=D.HCODOPE
where        eomonth(isnull(C.ROPEFEC,B.HFECDES))=eomonth(B.HFECPRO) and B.HINDCAR='VIGENTE' and B.HCODMOD<>100 and D.HCODOPE is null
group by    A.HFECPRO,A.HNUMDOC,A.HTIPDOC,A.HPAIS,CASE WHEN A.HTIPDOC in (21,9,15) THEN  'Peruano' else 'Migrante' end
)
select        A.HFECPRO,isnull(cast(B.RDESTIP as varchar),'INDETERMINADO') HINDRUR,HINDMIG
            ,count(distinct concat_ws('-',HPAIS,HTIPDOC,HNUMDOC)) NROCLI
from        cte_a A
left join    storage.[ref].[VURBRUR01] B
on            right(concat(replicate('0',6),A.HUBIGEO),6)=B.RCODUBI
--where        B.RDESTIP is null
group by    A.HFECPRO,B.RDESTIP,HINDMIG
order by 1,2,3

drop table #FECS
drop table #FECSCIEMES
drop table #FECSCIEBT