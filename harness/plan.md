# Plan

## Tickets

| Ticket | Status |
|--------|--------|
| FL-T1  | done |
| FL-T2 | done |

### FL-T1. Airfield location as geolocation (GPS)

#### Intent

Replace the airfield's free-text `location` with required GPS coordinates: `latitude` and `longitude`, expressed as decimal degrees. Store the coordinates as numeric values, not as a formatted string.

#### Scope and contract

- Replace `location` with numeric `latitude` and `longitude` fields in airfield create/update requests and airfield responses. This applies to create, update, get, and list API operations; flight references to an airfield do not change.
- Both coordinates are required. Latitude is in the inclusive range -90 through 90; longitude is in the inclusive range -180 through 180.
- Do not attempt to geocode existing free-text values. The application uses an in-memory H2 database, so no durable production data migration or preservation of existing location strings is in scope.
- Update generated API types/contracts through the documented generation workflow; do not edit generated files by hand.
- In the frontend, provide separate required numeric inputs for latitude and longitude when creating and editing an airfield. Display both coordinates in airfield list and detail views. Submit JSON numbers, not strings.

#### Mechanical checks (the Evaluator re-runs)

Durable automated tests (join the build):

- Backend API tests cover valid coordinates on create and update, and verify coordinates in get/list responses.
- Backend API tests cover missing coordinates and values outside each inclusive range. Out-of-range values return HTTP 400 through the existing request-validation handler and retain the existing error response shape; do not introduce a new status or error format.
- Frontend tests cover the two numeric create/edit fields and verify that valid values are sent as JSON numbers.
- Backend checks pass: from `backend/`, run `./gradlew test` and `./gradlew checkstyleMain`.
- Frontend checks pass: from `frontend/`, run `npm run typecheck`, `npm test`, and `npm run build`.
- When backend API annotations change, run `./gradlew generateOpenApi` from `backend/`, review the generated contract, then run `npm run api:types` from `frontend/` and review the generated TypeScript types.

One-time manual verification (in addition to automated tests):

- Confirm the create/edit UI shows separate latitude and longitude inputs and the list/detail views display both values.

#### Acceptance criteria (the Evaluator judges)

- The API and persisted airfield entity use required numeric `latitude` and `longitude` fields; the old free-text `location` field is no longer part of the airfield API.
- Both values are constrained to the stated inclusive ranges. Invalid coordinates return HTTP 400 through the existing validation path and preserve the current error shape.
- The create/edit forms accept and submit valid coordinates as numbers, and airfield list/detail views display both coordinates.
- Generated API artifacts match the backend request/response annotations.

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