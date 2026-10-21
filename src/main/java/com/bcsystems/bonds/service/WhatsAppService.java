package com.bcsystems.bonds.service;

public interface WhatsAppService {
    void notificarCreditoAsignado(com.bcsystems.bonds.event.CreditoAsignadoEvent event);
    void notificarAutorizacion(String tipo, String referencia, String detalle, String codigo, String solicitante);
    void enviarItinerarios();
}
