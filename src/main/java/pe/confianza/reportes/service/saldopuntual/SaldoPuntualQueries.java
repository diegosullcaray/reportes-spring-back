package pe.confianza.reportes.service.saldopuntual;

/** SQL migrado 1:1 desde "Reporte saldo puntual - saldo medio/Query.sql" (AR-03). */
public final class SaldoPuntualQueries {

    private SaldoPuntualQueries() {
    }

    public static final String SALDO_PUNTUAL = """
            SET NOCOUNT ON;
            declare @date date=:corte
            ;with cte_a as (
            select * from storage.[com_pas].[sdps013]
            where SFECPRO=@date and SCODAGR=8
            ),
            cte_aa as (
            select p.RDESCPROD02,b.*
            from cte_a b
            left join storage.[com_pas].[RETP001] P
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
            LEFT join  storage.ref.VJERCOR04 b
            on a.SSUCCLI=b.RCODAGE
            )
            SELECT * FROM CTE_B
            """;

    public static final String SALDO_MEDIO = """
            SET NOCOUNT ON;
            declare @date date=:corte
            ;with cte_a as (
            select * from storage.com_pas.wjas004 where   HTIPCOD=4 AND HFECPRO in(@date)
            )
            select distinct a.hfecpro,b.RDESAGEH,a.RDESCPROD02,a.HSALMEDMN
            from cte_a a
            inner join storage.ref.VJERCOR04 b
            on a.HCODREL=b.RCODAGEH
            order by a.HFECPRO asc
            """;
}
