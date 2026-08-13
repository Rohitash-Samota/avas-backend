# AVAS report sample

`AVAS_Demo_Duplex_Options_Cost_Report.pdf` is a vector-only example produced by the same
server report components used by the authenticated project and estimate PDF endpoints.

The report intentionally uses clearly marked `DEMO-EVIDENCE-*` price records to demonstrate
brand, product, supplier, tax and evidence provenance. It is a planning example, not a live
supplier quotation. Production reports expose a brand only when the corresponding current
price submission has been approved by an administrator; otherwise they state that no brand was
recorded.

Regenerate the sample from the same tested renderer with:

```bash
mvn -q -Dtest=ProjectReportPdfServiceTest \
  -Davas.sample.report=samples/AVAS_Demo_Duplex_Options_Cost_Report.pdf test
```
