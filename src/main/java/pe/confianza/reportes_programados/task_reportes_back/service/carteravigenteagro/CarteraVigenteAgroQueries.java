package pe.confianza.reportes_programados.task_reportes_back.service.carteravigenteagro;

/**
 * SQL migrado 1:1 desde "Reporte Giovanni -SaldoVigente_clientes productos_MENSUAL_AGRO.sql"
 * (AR-03). El script original comparte tablas temporales entre tres selects; aquí
 * cada dataset es un batch autocontenido (mismas CTEs) y @fec_ant se deriva del
 * corte con eomonth(@fec,-1), que es lo que fijaba el script a mano.
 */
public final class CarteraVigenteAgroQueries {

    private CarteraVigenteAgroQueries() {
    }

    private static final String DATA_ACTUAL = """
            ;with cte_data as (
            select  B.*,EP03.RDESPROD
            from storage.com_act.hcda001 B
            left join storage.[com_act].RETP001 EP01
             on EP01.RCODMOD=B.HCODMOD and EP01.RTIPOPE=B.HTIPOPE
             left join storage.[com_act].RETP002 EP02
             on EP02.RCODMOD=B.HCODMOD and EP02.RTIPOPE=B.HTIPOPE and EP02.RSUBTIP=B.HSUBTIP
             left join storage.[com_act].RETP003 EP03
             on EP03.RCODPROD=isnull(EP02.RCODPROD,EP01.RCODPROD)
             WHERE B.HFECPRO=@fec
             )
             select * into #Data from cte_data  WHERE RDESPROD='AGROPECUARIO'
            """;

    private static final String DATA_ANTERIOR = """
            ;with cte_data_ante as (
            select  B.*,EP03.RDESPROD
            from storage.com_act.hcda001 B
            left join storage.[com_act].RETP001 EP01
             on EP01.RCODMOD=B.HCODMOD and EP01.RTIPOPE=B.HTIPOPE
             left join storage.[com_act].RETP002 EP02
             on EP02.RCODMOD=B.HCODMOD and EP02.RTIPOPE=B.HTIPOPE and EP02.RSUBTIP=B.HSUBTIP
             left join storage.[com_act].RETP003 EP03
             on EP03.RCODPROD=isnull(EP02.RCODPROD,EP01.RCODPROD)
             WHERE B.HFECPRO=@fec_ant
             )
             select * into #Data_ante from cte_data_ante  WHERE RDESPROD='AGROPECUARIO'
            """;

    public static final String SALDO_VIGENTE_ACTUAL = """
            SET NOCOUNT ON;
            declare @fec date=:corte
            """ + DATA_ACTUAL + """
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
            """;

    public static final String SALDO_VIGENTE_ANTERIOR = """
            SET NOCOUNT ON;
            declare @fec date=:corte
            declare @fec_ant date=eomonth(@fec,-1)
            """ + DATA_ANTERIOR + """
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
            """;

    public static final String CLIENTES = """
            SET NOCOUNT ON;
            declare @fec date=:corte
            declare @fec_ant date=eomonth(@fec,-1)
            """ + DATA_ACTUAL + DATA_ANTERIOR + """
            ;with cte_cierre as(
             select hfecpro,haseoper,count(distinct concat_ws('-',hnumdoc,htipdoc,hpais)) codigoper
             from #Data
             group by hfecpro,haseoper
             )		,
             cte_cierre_mes as(
             select hfecpro,haseoper,count(distinct concat_ws('-',hnumdoc,htipdoc,hpais)) codigoper
             from  #Data_ante
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
             into #R
             FROM CTE_D A
             left JOIN (select * from storage.ref.wjercor03 where rfecpro=@fec and rindfec='actual')b
             ON A.HASEOPER=B.RCODSEC

            select hfecpro fecha,RDESGRU grupo,RDESTER territorio,RDESCOR corredor,RDESUNI unidad,haseoper sectorista,CierreMesAnterior,CierreActual,LOGICA
            from #R where RDESGRU is not null order by 1 asc
            """;
}
