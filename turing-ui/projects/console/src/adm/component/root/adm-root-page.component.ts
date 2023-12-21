import { Component, OnInit } from '@angular/core';
import { NotifierService } from 'angular-notifier-updated';
import { Router } from '@angular/router';

@Component({
  selector: 'adm-root-page',
  templateUrl: './adm-root-page.component.html'
})
export class TurAdmRootPageComponent implements OnInit {

  constructor(private readonly notifier: NotifierService, private router: Router) {

  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
