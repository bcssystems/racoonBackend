package com.bcsystems.bonds.service;

public interface WhatsAppService {
    void notificarCreditoAsignado(com.bcsystems.bonds.event.CreditoAsignadoEvent event);
    void notificarAutorizacion(String tipo, String referencia, String detalle, String codigo, String solicitante);
    void notificarSolicitudCredito(com.bcsystems.bonds.event.SolicitudCreditoCreadaEvent event);
    void enviarItinerarios();
    void notificarSolicitudCreditoResuelta(com.bcsystems.bonds.event.SolicitudCreditoResueltaEvent event);
    void notificarVentaCreditoCreada(com.bcsystems.bonds.event.VentaCreditoCreadaEvent event);
    void notificarAbonoRegistrado(com.bcsystems.bonds.event.AbonoRegistradoEvent event);
    void notificarVentaCancelada(com.bcsystems.bonds.event.VentaCanceladaEvent event);
}
