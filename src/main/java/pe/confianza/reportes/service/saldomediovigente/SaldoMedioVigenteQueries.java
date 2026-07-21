package pe.confianza.reportes.service.saldomediovigente;

/**
 * SQL migrado 1:1 desde "Reporte saldo medio vigente - Diana/Query.sql" (AR-03).
 * El rango diario del mes se deriva del propio corte con datefromparts.
 */
public final class SaldoMedioVigenteQueries {

    private SaldoMedioVigenteQueries() {
    }

    public static final String SALDO_MEDIO_VIGENTE = """
            SET NOCOUNT ON;
            declare @fec date=:corte
            select HFECPRO,HSALMEDMNVIGE  from storage.com_Act.wjas001 where hfecpro=@fec and htipcod=7
            """;

    public static final String SALDO_VIGENTE_DIARIO = """
            SET NOCOUNT ON;
            declare @fec date=:corte
            select sfecpro,ssalvigmn  from storage.com_act.sdas001
            where sfecpro between datefromparts(year(@fec),month(@fec),1) and @fec and scodagr=1 order by 1 asc
            """;
}
