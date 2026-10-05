# Plan

## Tickets

| Ticket | Status |
|--------|--------|
| Bug-01  | to-do |
| FL-T2 | done |

### Bug-01. Create Application Period doesn't work

#### Intent

In the frontend under "Applications" when I try to configure a period ("+configure period"), the "create peroid" button stays disabled regardless of the inputs (year, opens, closes, selected projects). Fix it so that it displays what's missing / wrong or enables the button when all necessary data exists.

#### Scope and contract


- Update generated API types/contracts through the documented generation workflow; do not edit generated files by hand.

#### Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend checks pass: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend checks pass: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.
- When backend API annotations change, run `./gradlew generateOpenApi` from `backend/`, review the generated contract, then run `npm run api:types` from `frontend/` and review the generated TypeScript types.

One-time manual verification (in addition to automated tests):

- Confirm the shows an active "create period" button or indicates what is missing to be able to create a period.

#### Acceptance criteria (the Evaluator judges)


### FL-T2. Add model type and image

#### Intent

Add a required aircraft type and an optional uploaded image for each model. Show the image in model and flight lists and on model detail pages. If no custom image is uploaded, show a type-specific default.

#### Scope and contract

- Define `ModelType` as the fixed enum `PLANE`, `HELICOPTER`, `DRONE`, and `OTHER`. The model create/update JSON requests require `type`. Reject missing or unknown values through the existing API error response format.
- Existing models without a type are assigned `OTHER`.
- Keep model create/update as JSON. Add `PUT /api/models/{id}/image` for multipart image upload, `GET /api/models/{id}/image` to serve the current image, and `DELETE /api/models/{id}/image` to remove a custom image and restore the default for the model's current type.
- Model responses include an `imageUrl`, not encoded image bytes. The image URL returns the custom image when present, otherwise the bundled default image for the model type.
- Image upload and deletion use the same authorization as model update. Image retrieval uses the same authorization as model retrieval.
- Accept JPEG and PNG uploads up to 5 MiB, with each source dimension at most 4096 pixels and at most 16 megapixels total. Reject unsupported, corrupt, or oversized images using the existing API error response format.
- Normalize accepted uploads to PNG, fitting within 512 by 512 pixels while preserving aspect ratio and not upscaling. Store only the normalized image bytes in the existing H2 database; do not add an image-processing dependency.
- Bundle a distinct default image for each `ModelType`. A model without a custom image uses the default matching its type. Changing type updates that default; changing other model fields preserves a custom image.
- In the frontend, add a required type selector and optional image file input to create and edit forms. On create, submit the model before uploading its optional image. On edit, keep the existing image unless a replacement is uploaded; provide a way to remove the custom image and restore the default.
- Display the model type and image in model list/detail views and display the model image in the flights list. Use `imageUrl`; do not include image bytes in model or flight JSON responses.
- Regenerate OpenAPI and frontend API types through the documented workflow. Review generated artifacts; do not edit them by hand or add generated `contracts/openapi.yaml` to version control.

#### Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend API tests cover all supported types, missing and unknown types, and type plus `imageUrl` in create/update/get/list responses.
- Backend upload tests cover JPEG and PNG success, unsupported and corrupt files, uploads over 5 MiB, source dimensions over 4096 pixels, and images over 16 megapixels.
- Backend image tests verify normalized output is PNG, fits within 512 by 512 pixels, preserves aspect ratio, and is not upscaled.
- Backend tests verify each type gets its distinct default image, changing type selects the corresponding default when no custom image exists, metadata updates preserve a custom image, and deleting a custom image restores the current type's default.
- Authorization tests verify upload and delete permissions match model update permissions, and image retrieval permissions match model retrieval permissions.
- Frontend tests cover type selection and image upload on create and edit, retaining or replacing an image, restoring the default, and displaying images in model list/detail and flight list views.
- Backend checks pass: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend checks pass: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.
- When backend API annotations change, run `./gradlew generateOpenApi` from `backend/`, review the generated contract, then run `npm run api:types` from `frontend/` and review the generated TypeScript types.

One-time manual verification (in addition to automated tests):

- Confirm image previews, fallback images, replacement, and reset work in the forms; confirm images render in model list/detail and flight list views.

#### Acceptance criteria (the Evaluator judges)

- Models have a required `ModelType` and responses expose `imageUrl`; existing models without a type resolve to `OTHER`.
- Uploads follow the documented format, size, dimension, and normalization rules and are stored as normalized image bytes.
- Each model type has a distinct default image. Custom images persist through metadata updates, and removing one restores the default for the current type.
- Upload, retrieval, and deletion follow the specified authorization rules.
- The create/edit UI supports type and image controls, and model list/detail and flight list views display the correct image.
- Generated API artifacts match backend annotations. The generated OpenAPI file is reviewed but not committed.