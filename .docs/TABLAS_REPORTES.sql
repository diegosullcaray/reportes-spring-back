/* ============================================================================
   TABLAS_REPORTES.sql — Inventario de tablas/vistas usadas por task-reportes-back
   ----------------------------------------------------------------------------
   Propósito: saber exactamente qué objetos hay que tener CARGADOS/ACTUALIZADOS
   para que cada reporte salga con datos completos, y verificar de un vistazo
   hasta qué fecha está cargado cada uno.

   Secciones:
     1. Inventario comentado, objeto por objeto, agrupado por reporte.
     2. Query de frescura: última fecha cargada por cada tabla con columna de
        fecha de proceso (ejecutar tal cual en SSMS).
     3. Objetos de referencia (sin fecha), funciones y procedimientos.
   ========================================================================== */

/* ============================================================================
   1. INVENTARIO POR REPORTE
   ----------------------------------------------------------------------------

   [cartera-heredada]  (bases dma y dwh)
     dma.dbo.FecCieBt          fechas de cierre BT (RFCIEBT)
     dma.dbo.HisCreditos       histórico de créditos (hfecpro)
     dma.dbo.HisGruposPDM      grupos PDM históricos (HFECPRO)
     dma.dbo.MrvGrupoPDM       maestro de grupos PDM
     dwh.dbo.BREGMOD001        módulos
     dwh.dbo.RTIPCRE001/2/3    tipificación de crédito → producto
     dwh.dbo.BREGUBT001        usuarios BT
     dwh.dbo.RREGOPE001        operaciones (RSECOPE)

   [desembolso-canal]
     storage.ref.RTCM001       tipo de cambio (RFECCIE)
     storage.com_act.HCDA001   cartera activa diaria (HFECPRO)
     storage.com_act.HMCM001   metas comerciales (HFECPRO)
     storage.gpr.VPPH001       planilla / personas
     storage.com_act.RFOC001   fecha real de operación
     storage.com_act.HCDR001   refinanciados (HFECPRO)
     storage.com_act.HCDR002   reprogramados (HFECPRO)
     storage.com_act.HDCE001   desembolsos canal electrónico (HFECPRO)
     storage.ref.FJERCOR02()   función: jerarquía comercial a una fecha

   [fondeo-estable]
     storage.com_pas.WJAS008   saldos agregados pasivos (hfecpro, HTIPCOD=4)
     storage.ref.vjercor04     jerarquía de agencias (matriz/macro/territorio)

   [saldo-medio-vigente]  (Diana)
     storage.com_act.wjas001   saldo medio vigente (hfecpro, htipcod=7)
     storage.com_act.sdas001   saldo vigente diario (sfecpro, scodagr=1)

   [saca-tu-garra]  (Giancarlo)
     storage.ref.RCALEN001     calendario de cierres (RFEC, RCIEBT=1)
     storage.com_act.HCTC001   traslados de cartera (HFECPRO)
     storage.com_act.HCDA001   cartera activa diaria (HFECPRO)
     storage.com_act.HCMA001   desembolsos del mes (HFECPRO)
     storage.com_act.SDAE002   recuperación 1-30 (sfecpro, scodagr=3)
     storage.com_act.SDAE003   recuperación 0-30 (sfecpro, scodagr=3)

   [datos-cierre]  (ratio CE + clientes rurales/migrantes)
     storage.com_act.wcdce001  desembolsos CE (hfecpro)
     storage.com_act.wcdce002  desembolsos habilitados CE (hfecpro)
     storage.ref.RCALEN001     calendario de cierres (RFEC, RCIEMES=1)
     storage.com_act.HBCN001   clientes nuevos (HFECPRO)
     storage.com_act.HCDA001   cartera activa diaria (HFECPRO)
     storage.com_act.RFOC001   fecha real de operación
     storage.com_act.HCDR001/2 refinanciados / reprogramados (HFECPRO)
     storage.ref.VURBRUR01     clasificación urbano/rural por ubigeo

   [reporte-seguros]  (Giovani)
     storage.com_act.SDAF002   operaciones por sectorista (SFECPRO, SCODAGR=6)
     storage.com_act.RETP001/2/3  tipificación → producto
     storage.com_seg.SDSF001   seguros colocados (sfecpro, scodagr=13)
     storage.ref.WJERCOR03     jerarquía comercial (RFECPRO, RINDFEC='ACTUAL')
     storage.com.VDMCOM01      metas (hfecpro, hcodvar=5001)

   [saldo-puntual-medio]  (Giovani)
     storage.com_pas.sdps013   saldo puntual pasivos (SFECPRO, SCODAGR=8)
     storage.com_pas.RETP001   tipificación de producto pasivos
     storage.com_pas.wjas004   saldo medio pasivos (HFECPRO, HTIPCOD=4)
     storage.ref.VJERCOR04     jerarquía de agencias

   [cartera-vigente-agro]  (Giovani)
     storage.com_act.hcda001   cartera activa diaria (HFECPRO)
     storage.com_act.RETP001/2/3  tipificación → producto (AGROPECUARIO)
     storage.ref.wjercor03     jerarquía comercial (rfecpro, rindfec='actual')
     storage.ref.FJERCOR02()   función: jerarquía a la fecha anterior

   [validacion-cubo]  (diaria)
     storage.cubo.VVALIDACIONCUBO01   indicadores del cubo comercial
                                      (TODO: confirmar vista/tabla real — el
                                      .docs solo trae el Excel de indicadores)

   [control-cargas]  (cada 5 min → Google Chat)
     mod_rep.com.RSRPD001 @OPT='2'    procedimiento: estado de los procesos
                                      de carga (des_pro, fec_act, fec_rep, est_pro)
   ========================================================================== */

/* ============================================================================
   2. FRESCURA DE DATOS — última fecha cargada por tabla
   (una fila por tabla; si una fecha quedó atrás, ese es el origen a recargar)
   ========================================================================== */
SELECT * FROM (
    -- dma / dwh (cartera-heredada)
    SELECT 'dma.dbo.FecCieBt'         tabla, 'cartera-heredada'                          reportes, MAX(RFCIEBT)  ultima_fecha FROM dma.dbo.FecCieBt
    UNION ALL SELECT 'dma.dbo.HisCreditos',    'cartera-heredada',                        MAX(hfecpro)  FROM dma.dbo.HisCreditos
    UNION ALL SELECT 'dma.dbo.HisGruposPDM',   'cartera-heredada',                        MAX(HFECPRO)  FROM dma.dbo.HisGruposPDM
    -- storage.com_act
    UNION ALL SELECT 'storage.com_act.HCDA001','desembolso-canal, saca-tu-garra, datos-cierre, cartera-vigente-agro', MAX(HFECPRO) FROM storage.com_act.HCDA001
    UNION ALL SELECT 'storage.com_act.HMCM001','desembolso-canal',                        MAX(HFECPRO)  FROM storage.com_act.HMCM001
    UNION ALL SELECT 'storage.com_act.HDCE001','desembolso-canal',                        MAX(HFECPRO)  FROM storage.com_act.HDCE001
    UNION ALL SELECT 'storage.com_act.HCDR001','desembolso-canal, datos-cierre',          MAX(HFECPRO)  FROM storage.com_act.HCDR001
    UNION ALL SELECT 'storage.com_act.HCDR002','desembolso-canal, datos-cierre',          MAX(HFECPRO)  FROM storage.com_act.HCDR002
    UNION ALL SELECT 'storage.com_act.HCTC001','saca-tu-garra',                           MAX(HFECPRO)  FROM storage.com_act.HCTC001
    UNION ALL SELECT 'storage.com_act.HCMA001','saca-tu-garra',                           MAX(HFECPRO)  FROM storage.com_act.HCMA001
    UNION ALL SELECT 'storage.com_act.SDAE002','saca-tu-garra',                           MAX(sfecpro)  FROM storage.com_act.SDAE002
    UNION ALL SELECT 'storage.com_act.SDAE003','saca-tu-garra',                           MAX(sfecpro)  FROM storage.com_act.SDAE003
    UNION ALL SELECT 'storage.com_act.wjas001','saldo-medio-vigente',                     MAX(hfecpro)  FROM storage.com_act.wjas001
    UNION ALL SELECT 'storage.com_act.sdas001','saldo-medio-vigente',                     MAX(sfecpro)  FROM storage.com_act.sdas001
    UNION ALL SELECT 'storage.com_act.wcdce001','datos-cierre',                           MAX(hfecpro)  FROM storage.com_act.wcdce001
    UNION ALL SELECT 'storage.com_act.wcdce002','datos-cierre',                           MAX(hfecpro)  FROM storage.com_act.wcdce002
    UNION ALL SELECT 'storage.com_act.HBCN001','datos-cierre',                            MAX(HFECPRO)  FROM storage.com_act.HBCN001
    UNION ALL SELECT 'storage.com_act.SDAF002','reporte-seguros',                         MAX(SFECPRO)  FROM storage.com_act.SDAF002
    -- storage.com_seg / com / com_pas
    UNION ALL SELECT 'storage.com_seg.SDSF001','reporte-seguros',                         MAX(sfecpro)  FROM storage.com_seg.SDSF001
    UNION ALL SELECT 'storage.com.VDMCOM01',   'reporte-seguros (metas)',                 MAX(hfecpro)  FROM storage.com.VDMCOM01
    UNION ALL SELECT 'storage.com_pas.WJAS008','fondeo-estable',                          MAX(hfecpro)  FROM storage.com_pas.WJAS008
    UNION ALL SELECT 'storage.com_pas.sdps013','saldo-puntual-medio',                     MAX(SFECPRO)  FROM storage.com_pas.sdps013
    UNION ALL SELECT 'storage.com_pas.wjas004','saldo-puntual-medio',                     MAX(HFECPRO)  FROM storage.com_pas.wjas004
    -- storage.ref
    UNION ALL SELECT 'storage.ref.RTCM001',    'desembolso-canal (tipo de cambio)',       MAX(RFECCIE)  FROM storage.ref.RTCM001
    UNION ALL SELECT 'storage.ref.RCALEN001',  'saca-tu-garra, datos-cierre (calendario)',MAX(RFEC)     FROM storage.ref.RCALEN001
    UNION ALL SELECT 'storage.ref.WJERCOR03',  'reporte-seguros, cartera-vigente-agro (jerarquía)', MAX(RFECPRO) FROM storage.ref.WJERCOR03
) t
ORDER BY t.tabla;

/* ============================================================================
   3. OBJETOS SIN COLUMNA DE FECHA (referenciales) Y PROGRAMABLES
   (no entran al query de frescura; verificar que existan y tengan datos)
   ========================================================================== */
-- Referenciales (maestros/jerarquías sin fecha de proceso):
--   dma.dbo.MrvGrupoPDM · dwh.dbo.BREGMOD001 · dwh.dbo.RTIPCRE001/2/3
--   dwh.dbo.BREGUBT001 · dwh.dbo.RREGOPE001 · storage.gpr.VPPH001
--   storage.com_act.RFOC001 · storage.com_act.RETP001/2/3
--   storage.com_pas.RETP001 · storage.ref.VURBRUR01
--   storage.ref.vjercor04 / VJERCOR04
--
-- Función:      storage.ref.FJERCOR02(@fecha)       jerarquía comercial a una fecha
-- Procedimiento: mod_rep.com.RSRPD001 @OPT='2'      estado de procesos de carga (control-cargas)
-- Pendiente:    storage.cubo.VVALIDACIONCUBO01      confirmar objeto real del cubo (validacion-cubo)

-- Conteo rápido de los referenciales (0 filas = problema):
SELECT 'dma.dbo.MrvGrupoPDM' objeto, COUNT(*) filas FROM dma.dbo.MrvGrupoPDM
UNION ALL SELECT 'dwh.dbo.BREGMOD001',  COUNT(*) FROM dwh.dbo.BREGMOD001
UNION ALL SELECT 'dwh.dbo.RTIPCRE003',  COUNT(*) FROM dwh.dbo.RTIPCRE003
UNION ALL SELECT 'dwh.dbo.BREGUBT001',  COUNT(*) FROM dwh.dbo.BREGUBT001
UNION ALL SELECT 'storage.gpr.VPPH001', COUNT(*) FROM storage.gpr.VPPH001
UNION ALL SELECT 'storage.com_act.RFOC001',  COUNT(*) FROM storage.com_act.RFOC001
UNION ALL SELECT 'storage.com_act.RETP003',  COUNT(*) FROM storage.com_act.RETP003
UNION ALL SELECT 'storage.com_pas.RETP001',  COUNT(*) FROM storage.com_pas.RETP001
UNION ALL SELECT 'storage.ref.VURBRUR01',    COUNT(*) FROM storage.ref.VURBRUR01
UNION ALL SELECT 'storage.ref.vjercor04',    COUNT(*) FROM storage.ref.vjercor04
ORDER BY 1;
