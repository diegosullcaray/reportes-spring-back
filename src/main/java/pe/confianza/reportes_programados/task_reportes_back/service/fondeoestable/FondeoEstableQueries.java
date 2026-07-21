package pe.confianza.reportes_programados.task_reportes_back.service.fondeoestable;

/** SQL migrado 1:1 desde "FondeoEstable.sql" (AR-03); corte como :corte (date). */
public final class FondeoEstableQueries {

    private FondeoEstableQueries() {
    }

    public static final String SALDO_FONDEO_ESTABLE = """
            SET NOCOUNT ON;
            ;with cte_a as (
            select * from STORAGE.[com_pas].[WJAS008]
            where hfecpro=:corte and HTIPCOD=4 and  RDESCPROD='TODOS'
            )
            select A.HFECPRO FECHA,b.RDESMAT,A.HSALFESI  from cte_a a
            left join (select distinct RCODMAT,RDESMAT from storage.ref.vjercor04)  b
            on a.HCODREL=b.RCODMAT
            """;
}
