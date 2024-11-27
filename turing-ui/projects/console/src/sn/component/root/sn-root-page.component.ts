import { Component, OnInit } from '@angular/core';
import { NotifierService } from 'angular-notifier-updated';
import { Router } from '@angular/router';

@Component({
    selector: 'sn-root-page',
    templateUrl: './sn-root-page.component.html',
    standalone: false
})
export class TurSNRootPageComponent implements OnInit {

  constructor(private readonly notifier: NotifierService, private router: Router) {

  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
