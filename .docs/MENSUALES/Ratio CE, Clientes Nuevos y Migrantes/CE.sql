declare @numope int,@mondes float,@fecha date
/* Desembolsos habilitados posibles desembolsos CE */
select count(distinct HCODOPE),sum(HMONDESMN) from storage.com_act.wcdce002
where hfecpro ='20260630'
group by hfecpro order by 1 asc
 
  


/* Desembolsos CE */
select count(distinct HCODOPE),sum(HMONDESMN) from storage.com_act.wcdce001
where hfecpro ='20260630'
group by hfecpro order by 1 asc
   
    