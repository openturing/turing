import { Component, OnInit } from '@angular/core';
import { NotifierService } from 'angular-notifier';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'dev-root-page',
  templateUrl: './dev-root-page.component.html'
})
export class TurDevRootPageComponent implements OnInit {

  constructor(private readonly notifier: NotifierService, private router: Router) {

  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
