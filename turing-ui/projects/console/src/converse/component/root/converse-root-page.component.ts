import { Component, OnInit } from '@angular/core';
import { NotifierService } from 'angular-notifier-updated';
import { Router } from '@angular/router';

@Component({
  selector: 'converse-root-page',
  templateUrl: './converse-root-page.component.html'
})
export class TurConverseRootPageComponent implements OnInit {

  constructor(private readonly notifier: NotifierService, private router: Router) {

  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
