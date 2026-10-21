package com.bcsystems.bonds.event;

public record CreditoAsignadoEvent(Integer idCredito, Integer idCobradorAnterior, Integer idCobradorNuevo) {}
