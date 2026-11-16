package com.navan.controller;

import com.navan.dto.InvoiceRequest;
import com.navan.dto.InvoiceResponse;
import com.navan.dto.PageResponse;
import com.navan.service.InvoiceService;
import com.navan.service.PageQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/invoices", produces = MediaType.APPLICATION_JSON_VALUE)
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InvoiceResponse> create(@RequestBody InvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.create(request));
    }

    @GetMapping
    public PageResponse<InvoiceResponse> list(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return invoiceService.list(PageQuery.parse(limit, offset));
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable String id) {
        return invoiceService.get(id);
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public InvoiceResponse update(@PathVariable String id, @RequestBody InvoiceRequest request) {
        return invoiceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
