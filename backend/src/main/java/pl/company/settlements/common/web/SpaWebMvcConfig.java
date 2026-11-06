package pl.company.settlements.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SpaWebMvcConfig implements WebMvcConfigurer {

    private static final String[] FRONTEND_ROUTES = {
            "/",

            "/tasks",
            "/tasks/new",
            "/tasks/{taskId:[^.]+}",
            "/tasks/{taskId:[^.]+}/edit",
            "/tasks/{taskId:[^.]+}/main-contracts/new",

            "/main-contracts/{contractId:[^.]+}",
            "/main-contracts/{contractId:[^.]+}/edit",
            "/main-contracts/{contractId:[^.]+}/attachments",
            "/main-contracts/{contractId:[^.]+}/amendments/new",
            "/main-contracts/{contractId:[^.]+}/amendments/{amendmentId:[^.]+}/edit",

            "/main-contracts/{contractId:[^.]+}/subcontracts/new",
            "/main-contracts/{contractId:[^.]+}/subcontracts/{subcontractId:[^.]+}",
            "/main-contracts/{contractId:[^.]+}/subcontracts/{subcontractId:[^.]+}/edit",
            "/main-contracts/{contractId:[^.]+}/subcontracts/{parentSubcontractId:[^.]+}/children/new",
            "/main-contracts/{contractId:[^.]+}/subcontracts/{subcontractId:[^.]+}/amendments/new",
            "/main-contracts/{contractId:[^.]+}/subcontracts/{subcontractId:[^.]+}/amendments/{amendmentId:[^.]+}/edit",

            "/main-contracts/{contractId:[^.]+}/invoices",
            "/main-contracts/{contractId:[^.]+}/invoices/new",
            "/main-contracts/{contractId:[^.]+}/invoices/{mainInvoiceId:[^.]+}",
            "/main-contracts/{contractId:[^.]+}/invoices/{mainInvoiceId:[^.]+}/edit",
            "/main-contracts/{contractId:[^.]+}/invoices/{mainInvoiceId:[^.]+}/payments",

            "/main-contracts/{contractId:[^.]+}/subcontracts/{subcontractId:[^.]+}/invoices",
            "/main-contracts/{contractId:[^.]+}/subcontracts/{subcontractId:[^.]+}/invoices/new",
            "/main-contracts/{contractId:[^.]+}/subcontracts/{subcontractId:[^.]+}/invoices/{invoiceId:[^.]+}/edit",
            "/main-contracts/{contractId:[^.]+}/subcontracts/{subcontractId:[^.]+}/invoices/{invoiceId:[^.]+}/payments",
            "/main-contracts/{contractId:[^.]+}/subcontracts/{subcontractId:[^.]+}/invoices/{invoiceId:[^.]+}/settlements",

            "/payments",
            "/payments/new",
            "/payments/{paymentId:[^.]+}",
            "/payments/{paymentId:[^.]+}/edit"
    };

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String route : FRONTEND_ROUTES) {
            registry.addViewController(route)
                    .setViewName("forward:/index.html");

            if (!"/".equals(route)) {
                registry.addViewController(route + "/")
                        .setViewName("forward:/index.html");
            }
        }
    }
}