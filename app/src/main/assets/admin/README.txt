Sursand Connect Super Admin v4.1

FIX
- Fixed blank Home screen in v4.
- The problem was a JavaScript name collision between the Login container id and login() function.
- Home/login elements now use explicit document.getElementById lookups.
- Login, session restore, module rendering, notification bell, menu and password controls are all initialized safely.
- Existing v4 separate module-page design and forms are unchanged.

NO APPS SCRIPT CHANGE REQUIRED for this v4.1 frontend fix.
