# Sursand Connect Admin Android — Clean v64
This is a clean repository. Upload these files to a NEW empty GitHub repository; do not merge them into the old Android repository.
Only `app/src/main/assets/admin/` is used. The old duplicate `assets/www/` tree has been removed.
On successful login/startup the Admin app fetches `adminDataAll` once and stores every module locally. Module pages open from local cache immediately and remain readable offline, including Representatives and App Settings after the first successful sync.
The current QR/business-card preview includes Print/PDF, Save JPG and Close controls and uses the Android native bridge.
