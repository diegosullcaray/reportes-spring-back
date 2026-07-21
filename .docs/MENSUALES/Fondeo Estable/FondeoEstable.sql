--Reportes Mensual Fondeo Estable 
--Eddy
;with cte_a as (
select * from STORAGE.[com_pas].[WJAS008]
where hfecpro='20260630' and HTIPCOD=4 and  RDESCPROD='TODOS' 
)
select A.HFECPRO FECHA,b.RDESMAT,A.HSALFESI  from cte_a a 
left join (select distinct RCODMAT,RDESMAT from storage.ref.vjercor04)  b
on a.HCODREL=b.RCODMAT 