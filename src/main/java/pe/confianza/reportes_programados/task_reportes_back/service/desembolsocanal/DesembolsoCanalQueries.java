package pe.confianza.reportes_programados.task_reportes_back.service.desembolsocanal;

/** SQL migrado 1:1 desde "Desembolsos_canal.sql" (AR-03); corte como :corte (date). */
public final class DesembolsoCanalQueries {

    private DesembolsoCanalQueries() {
    }

    public static final String DESEMBOLSOS_POR_CANAL = """
            SET NOCOUNT ON;
            declare @val float

            DECLARE @Fecha date=:corte

            select top 1 @val= RVALCA
                from storage.ref.RTCM001
                where RFECCIE<=@Fecha
                order by RFECCIE desc

            ;WITH AX as
             ( select HFECPRO,HROWID,HCODEMP,HHORREG,HNUMCOR,HCODMOD,HNUMTHR,HUSUPRO,HINDCAR
               ,case when A.HFECPRO>=cast('2021-01-30' as date) then case when HASEOPER='--' or HASEOPER is null then '' else HASEOPER end else HCODSEC end HCODSEC
               ,HSUCSEC,HDSUCSEC,HCRESBS
               ,case when A.HFECPRO>=cast('2021-01-30' as date) then case when HSUCOPER is null then 0 else HSUCOPER end else HSUCCLI end HSUCCLI
               ,HDSUCCLI,HCODCIIU,HDESCIIU,HNIVAPR
               ,HDNIVAPR,HCODCNV,HDCODCNV,HMONDES,HTOTCUO,HFECDES,HNUMTAS,HSALRIE,HTIPCAR
               ,HSUBTIP,HDSUBTIP,HTIPOPE,HDTIPOPE,HCTACLI,HDESCLI,HCODOPE,HSUBOPE,HSALCAR
               ,HSALVEN,HSALCAP,HCUOPEN,HCUOVEN,HDIAATR,HINTVEN,HVENATR,HINTMOR,HINTCMP
               ,HCODMON,HFECPRC,HFECMOR,HNUMLET,HNUMREN,HULTVEN,HCLFSBS,HFECCLF,HMONPRV
               ,HMONGAR,HTOTDEU,HTOTGAR,HTIPDOC,HNUMDOC,HRANDIA,HDESCRE,HCARCOB,HCARNCOB
               ,HINGDIF,HNOMREG,HDESMOD,HESTOPE,HDESTOPE,HSALCAPMN,HSALVENMN,HTIPCMB
               ,HDCODSEC,HINDCAM,HDINDCAM,HFRCPAG,HRIECBOPE,HRIECBCLI,HSHKCMB,HNUMCRCLI
               ,HCAPPEN,HNUMRUB,HDESRUB,HPRIVEN,HPAPEL,HINDTIPCB,HFECUCC,HTIPPER,HGENPER
               ,HFECPER,HSITCRE,HPAIS,HDPAIS,HUBIGEO,HRESID,HDIREC,HOCUP,HNASEORI,HASEORI
               ,HDOCFIA,HNOMFIA,HCTAFIA,HSUCOPER,HASEOPER,HCODSEC HCODSECANT
               ,concat(isnull([HNUMDOC],'00000000'),'-',isnull([HTIPDOC],'0'),'-',isnull([HPAIS],'0')) HDOCCON
               ,case when year(HFECMOR)=year(HFECPRO) and month(HFECMOR)=month(HFECPRO) then 1 else 0 end HINDIM
              from [storage].[com_act].[HCDA001] A
              where (HFECPRO)=@Fecha
             )
             ,BX as
             (
              select distinct HCODREL
              from  [storage].[com_act].[HMCM001]
              where  HFECPRO=eomonth(@Fecha)
               and HGRUREL='ASESOR'
               and not (HCODREL is null or HCODREL='N/A')
              UNION
              select distinct HCODREL
              from  [storage].[com_act].[HMCM001]
                 where HFECPRO=eomonth(@Fecha)
                 and HGRUREL='ASESOR'
                 and not (HCODREL is null or HCODREL='N/A')
             ),
             VCAH001 as (
             select  HFECPRO,HROWID,HCODEMP,HHORREG,HNUMCOR,HCODMOD,HNUMTHR,HUSUPRO,HINDCAR
                 ,HCODSEC
                 ,HSUCSEC,HDSUCSEC,HCRESBS
                 ,HSUCCLI
                 ,HDSUCCLI,HCODCIIU,HDESCIIU,HNIVAPR
                 ,HDNIVAPR,HCODCNV,HDCODCNV,HMONDES,HTOTCUO,HFECDES,HNUMTAS,HSALRIE,HTIPCAR
                 ,HSUBTIP,HDSUBTIP,HTIPOPE,HDTIPOPE,HCTACLI,HDESCLI,HCODOPE,HSUBOPE,HSALCAR
                 ,HSALVEN,HSALCAP,HCUOPEN,HCUOVEN,HDIAATR,HINTVEN,HVENATR,HINTMOR,HINTCMP
                 ,HCODMON,HFECPRC,HFECMOR,HNUMLET,HNUMREN,HULTVEN,HCLFSBS,HFECCLF,HMONPRV
                 ,HMONGAR,HTOTDEU,HTOTGAR,HTIPDOC,A.HNUMDOC,HRANDIA,HDESCRE,HCARCOB,HCARNCOB
                 ,HINGDIF,HNOMREG,HDESMOD,HESTOPE,HDESTOPE,HSALCAPMN,HSALVENMN,HTIPCMB
                 ,D.HDESPER HDCODSEC
                 ,HINDCAM,HDINDCAM,HFRCPAG,HRIECBOPE,HRIECBCLI,HSHKCMB,HNUMCRCLI
                 ,HCAPPEN,HNUMRUB,HDESRUB,HPRIVEN,HPAPEL,HINDTIPCB,HFECUCC,HTIPPER,HGENPER
                 ,HFECPER,HSITCRE,HPAIS,HDPAIS,HUBIGEO,HRESID,HDIREC,HOCUP,HNASEORI,HASEORI
                 ,HDOCFIA,HNOMFIA,HCTAFIA,HSUCOPER,HASEOPER
                 ,HDOCCON
                 ,HINDIM
                 ,HCODSECANT
               ,D.HNUMDOC HNDOCSEC,case when B.HCODREL is null then 0 else 1 end HINDMETA
             from  AX A
             left join storage.[gpr].VPPH001 D
             on   A.HCODSEC=D.HCODBT
             left join BX B
             on   D.HNUMDOC=B.HCODREL
             ),cte_z as
             ( select  A.*
               ,case when HCODMON=0 then HMONDES else HMONDES*@val end HMONDESMN,isnull(G.ROPEFEC,A.HFECDES) HFECDESA
              from   VCAH001 A
              left join [storage].[com_act].[RFOC001] G
              on A.[HCODOPE]=G.RCODOPE and A.[HINDCAR]<>'REFINANCIADO' and A.[HCODMOD]<>110
              where isnull(G.ROPEFEC,A.HFECDES)>=(select cast(DATEADD(MONTH, DATEDIFF(MONTH, 0, @Fecha), 0) as date))
              and HINDCAR='VIGENTE'
              and A.HCODOPE not in( select HCODOPE from storage.com_act.HCDR001 where HFECPRO=@Fecha and eomonth(HFECREP)=eomonth(HFECPRO)
                  UNION
                  select HCODOPE from storage.com_act.HCDR002 where HFECPRO=@Fecha and eomonth(HFECREP)=eomonth(HFECPRO))
             ),
             cte_ce as (
             SELECT A.HFECPRO,B.HCODSEC,A.HCODOPE,B.HCODMOD,B.HTIPOPE,B.HSUBTIP,B.HFECDESA,B.HMONDESMN,b.HDTIPOPE,b.HMONDES,B.HCTACLI
             FROM storage.com_act.HDCE001 A
             inner join  cte_z B
             on            A.HFECPRO=B.HFECPRO and A.HCODOPE=B.HCODOPE
             where         A.HCONTAB='SI'
             )
             ,cte_result1 as (
             select a.HDTIPOPE,a.HMONDES,a.HFECPRO,a.HCODSEC,a.HCODOPE,case when b.hcodope IS NULL THEN 'BT' else 'CT' end canal
             from cte_z a
             left join cte_ce b
             on a.HCODOPE=b.hcodope
             ),
             cte_resul as (
             select distinct isnull(b.RDESTER,'SIN ASIGNAR') RDESTER,ISNULL(b.RDESCOR,'SIN ASIGNAR') RDESCOR,
             ISNULL(b.rdesuni,'SIN ASIGNAR') rdesuni,
             isnull(b.RCODSEC ,'SIN ASIGNAR') RCODSEC, isnull(b.RDESSEC ,'SIN ASIGNAR') RDESSEC,
             a.HDTIPOPE,   count(distinct HCODOPE)   Ope,sum(HMONDES) Monto,a.canal
             from cte_result1 a
             left join  (select * from storage.ref.FJERCOR02(@Fecha)) b
             on a.HCODSEC=b.RCODSEC
             group by  b.RDESTER,b.RDESCOR,b.rdesuni,b.RCODSEC,b.RDESSEC,a.HDTIPOPE,a.canal
             )
             select * from cte_resul
            """;
}
